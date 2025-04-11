import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import DifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import { getDocumentPairs, getDocumentPageDetails } from '../../services/api';
import './SideBySideView.css';

const SideBySideView = ({ comparisonId }) => {
  // Refs
  const containerRef = useRef(null);
  const fetchingRef = useRef(false);
  const fetchFailedRef = useRef(false);

  // Context
  const {
    state,
    setError,
    setLoading,
    setDocumentPairs,
    setSelectedDocumentPairIndex,
    setSelectedDifference
  } = useComparison();

  // State
  const [activeView, setActiveView] = useState('matching');
  const [currentPage, setCurrentPage] = useState(1);
  const [baseDifferences, setBaseDifferences] = useState([]);
  const [compareDifferences, setCompareDifferences] = useState([]);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [zoom, setZoom] = useState(1.0);

  // Get the currently selected document pair
  const selectedPair = state.documentPairs?.[state.selectedDocumentPairIndex] || null;

  // Calculate max pages
  const maxPageCount = selectedPair ? 
    Math.max(
      selectedPair.baseEndPage - selectedPair.baseStartPage + 1,
      selectedPair.compareEndPage - selectedPair.compareStartPage + 1
    ) : 1;

  // Fetch document pairs - with circuit breaker protection
  useEffect(() => {
    const fetchDocumentPairs = async () => {
      // Guard against duplicate fetches or retries after too many failures
      if (!comparisonId || fetchingRef.current || fetchFailedRef.current) return;
      
      try {
        fetchingRef.current = true;
        setLoading(true);
        console.log("Fetching document pairs...");
        
        const pairs = await getDocumentPairs(comparisonId);
        console.log("Received document pairs:", pairs);
        
        if (pairs && pairs.length > 0) {
          setDocumentPairs(pairs);
          
          // Auto-select the first matched pair
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;
          
          setSelectedDocumentPairIndex(initialIndex);
          
          if (matchedPairIndex >= 0) {
            setActiveView('comparison');
          }
        }
      } catch (err) {
        console.error("Error fetching document pairs:", err);
        setError("Failed to load document pairs: " + err.message);
        
        // Mark as failed to prevent endless retries
        if (err.message && (
          err.message.includes("Service temporarily unavailable") ||
          err.message.includes("Network Error") ||
          err.message.includes("Circuit breaker is open")
        )) {
          fetchFailedRef.current = true;
          console.log("API appears to be unavailable, stopping retries");
        }
      } finally {
        setLoading(false);
        fetchingRef.current = false;
      }
    };

    fetchDocumentPairs();
  }, [comparisonId]); // Remove unnecessary dependencies to prevent infinite loops

  // Fetch page differences
  useEffect(() => {
    const fetchDifferences = async () => {
      if (!comparisonId || !selectedPair || activeView !== 'comparison') return;
      
      try {
        console.log(`Fetching differences for page ${currentPage}...`);
        
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          currentPage,
          state.filters
        );
        
        if (details) {
          console.log("Received difference details:", details);
          setBaseDifferences(details.baseDifferences || []);
          setCompareDifferences(details.compareDifferences || []);
        }
      } catch (err) {
        console.error("Error fetching differences:", err);
        setError("Failed to load differences: " + err.message);
      }
    };

    fetchDifferences();
  }, [comparisonId, selectedPair, currentPage, state.selectedDocumentPairIndex, state.filters, activeView]);

  // Handle document pair selection
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("Selecting document pair:", pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    setCurrentPage(1);
    setActiveView('comparison');
  };

  // Handle zoom change
  const handleZoomChange = (newZoom) => {
    setZoom(newZoom);
    
    // Update in comparison context if needed
    if (state.viewSettings?.zoom !== newZoom) {
      state.updateViewSettings?.({ zoom: newZoom });
    }
  };

  // If we're in document matching view
  if (activeView === 'matching') {
    return (
      <DocumentMatchingView
        comparisonId={comparisonId}
        onSelectDocumentPair={handleSelectDocumentPair}
      />
    );
  }

  // Show loading state if no document pair is selected
  if (!selectedPair) {
    return (
      <div className="loading-container">
        <Spinner size="large" />
        <p>Loading document comparison...</p>
      </div>
    );
  }

  // Calculate actual page numbers
  const basePageNum = selectedPair.baseStartPage + currentPage - 1;
  const comparePageNum = selectedPair.compareStartPage + currentPage - 1;
  
  // Check if pages are out of bounds
  const baseOutOfBounds = basePageNum > selectedPair.baseEndPage;
  const compareOutOfBounds = comparePageNum > selectedPair.compareEndPage;

  return (
    <div className="pdf-comparison-container">
      {/* Header */}
      <div className="comparison-header">
        <button 
          className="back-button"
          onClick={() => setActiveView('matching')}
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
          </svg>
          Back to Document Matching
        </button>
        
        <div className="document-info">
          <h3>Document Comparison</h3>
          
        </div>
        <div className="page-ranges">
        <span>Base: Pages {selectedPair.baseStartPage}-{selectedPair.baseEndPage} - Compare: Pages {selectedPair.compareStartPage}-{selectedPair.compareEndPage}</span>

          </div>
        
        {/* Zoom controls */}
        <div className="zoom-controls">
          <button 
            onClick={() => handleZoomChange(Math.max(0.5, zoom - 0.25))}
            title="Zoom Out"
          >
            -
          </button>
          
          <span>{Math.round(zoom * 100)}%</span>
          
          <button 
            onClick={() => handleZoomChange(Math.min(3.0, zoom + 0.25))}
            title="Zoom In"
          >
            +
          </button>
        </div>
      </div>
      
      {/* Main content with single scrollbar */}
      <div className="comparison-content">
        <div 
          className={`documents-scroll-container ${showDifferencePanel ? 'with-panel' : ''}`}
          ref={containerRef}
        >
          <div className="documents-wrapper">
            {/* Base document */}
            <div className="document-view base-document">
              <div className="document-header">
                <h4>Base Document</h4>
                <div className="page-info">
                  Page {basePageNum} of {selectedPair.baseEndPage}
                </div>
              </div>
              
              <div className="document-content">
                {baseOutOfBounds ? (
                  <div className="no-content">
                    <p>Page {basePageNum} is not available in the base document</p>
                  </div>
                ) : state.baseFile?.fileId ? (
                  <PDFRenderer
                    fileId={state.baseFile.fileId}
                    page={basePageNum}
                    zoom={zoom}
                    highlightMode={highlightMode}
                    differences={baseDifferences}
                    selectedDifference={state.selectedDifference}
                    onDifferenceSelect={(diff) => setSelectedDifference(diff)}
                    onZoomChange={handleZoomChange}
                    isBaseDocument={true} // This is the base document
                  />
                ) : (
                  <div className="no-content">
                    <p>Base file not available</p>
                  </div>
                )}
              </div>
            </div>
            
            {/* Compare document */}
            <div className="document-view compare-document">
              <div className="document-header">
                <h4>Compare Document</h4>
                <div className="page-info">
                  Page {comparePageNum} of {selectedPair.compareEndPage}
                </div>
              </div>
              
              <div className="document-content">
                {compareOutOfBounds ? (
                  <div className="no-content">
                    <p>Page {comparePageNum} is not available in the compare document</p>
                  </div>
                ) : state.compareFile?.fileId ? (
                  <PDFRenderer
                    fileId={state.compareFile.fileId}
                    page={comparePageNum}
                    zoom={zoom}
                    highlightMode={highlightMode}
                    differences={compareDifferences}
                    selectedDifference={state.selectedDifference}
                    onDifferenceSelect={(diff) => setSelectedDifference(diff)}
                    onZoomChange={handleZoomChange}
                    isBaseDocument={false} // This is the compare document
                  />
                ) : (
                  <div className="no-content">
                    <p>Compare file not available</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
        
        {/* Difference panel */}
        {showDifferencePanel && (
          <div className="difference-panel">
            <div className="difference-panel-header">
              <h3>Differences</h3>
            </div>
            <DifferenceList
              result={{ 
                baseDifferences, 
                compareDifferences 
              }}
              selectedDifference={state.selectedDifference}
              onDifferenceClick={(diff) => setSelectedDifference(diff)}
            />
          </div>
        )}
        
        {/* Toggle button */}
        <button 
          className={`toggle-panel-button ${showDifferencePanel ? '' : 'hidden'}`}
          onClick={() => setShowDifferencePanel(!showDifferencePanel)}
        >
          {showDifferencePanel ? '→' : '←'}
        </button>
      </div>
      
      {/* Footer */}
      <div className="comparison-footer">
        <div className="page-navigation">
          <button 
            onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
            disabled={currentPage <= 1}
          >
            Previous Page
          </button>
          
          <span className="page-indicator">
            Page {currentPage} of {maxPageCount}
          </span>
          
          <button 
            onClick={() => setCurrentPage(Math.min(maxPageCount, currentPage + 1))}
            disabled={currentPage >= maxPageCount}
          >
            Next Page
          </button>
        </div>
        
        <div className="view-controls">
          <div className="highlight-controls">
            <select 
              value={highlightMode}
              onChange={(e) => setHighlightMode(e.target.value)}
            >
              <option value="all">All Differences</option>
              <option value="text">Text Only</option>
              <option value="image">Images Only</option>
              <option value="font">Fonts Only</option>
              <option value="style">Styles Only</option>
              <option value="none">No Highlights</option>
            </select>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SideBySideView;