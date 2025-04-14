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
  const pairsFetchedRef = useRef(false);
  const didInitialFetchRef = useRef(false);

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
  const [zoom, setZoom] = useState(0.750);
  const [pageMetadata, setPageMetadata] = useState(null);
  
  // Coordinate adjustment fine-tuning parameters
  const [adjustmentParams, setAdjustmentParams] = useState({
    xOffset: 0, // X-axis offset in pixels
    yOffset: 0, // Y-axis offset in pixels
    scaleAdjustment: 1.0, // Scale multiplier
    flipY: true // Whether to flip the Y-coordinate (fixes upside-down canvas)
  });
  
  // Flag to show adjustment controls for debugging
  const [showAdjustmentControls, setShowAdjustmentControls] = useState(false);

  // Get the currently selected document pair
  const selectedPair = state.documentPairs?.[state.selectedDocumentPairIndex] || null;

  // Calculate max pages
  const maxPageCount = selectedPair ? 
    Math.max(
      selectedPair.baseEndPage - selectedPair.baseStartPage + 1,
      selectedPair.compareEndPage - selectedPair.compareStartPage + 1
    ) : 1;

  // CRITICAL FIX: Only fetch document pairs once on initial render
  useEffect(() => {
    // Skip if no comparison ID or if we've already fetched
    if (!comparisonId || didInitialFetchRef.current) {
      return;
    }

    const fetchDocumentPairs = async () => {
      // Set our flag immediately to prevent concurrent fetches
      didInitialFetchRef.current = true;
      
      // Skip if already fetching
      if (fetchingRef.current) return;
      
      try {
        fetchingRef.current = true;
        setLoading(true);
        console.log("Fetching document pairs...");
        
        const pairs = await getDocumentPairs(comparisonId);
        console.log("Received document pairs:", pairs);
        
        if (pairs && pairs.length > 0) {
          // Store in context
          setDocumentPairs(pairs);
          pairsFetchedRef.current = true;
          
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
        
        // Reset flag on serious error so we can retry
        if (err.message && (
          err.message.includes("Service temporarily unavailable") ||
          err.message.includes("Network Error") ||
          err.message.includes("Circuit breaker is open")
        )) {
          didInitialFetchRef.current = false;
        }
      } finally {
        setLoading(false);
        fetchingRef.current = false;
      }
    };

    fetchDocumentPairs();
    
    // Cleanup function
    return () => {
      // Reset on component unmount, not on dependency change
      if (!comparisonId) {
        didInitialFetchRef.current = false;
        pairsFetchedRef.current = false;
      }
    };
  }, [comparisonId]); // Only depend on comparisonId

  // Handle active view changes separately
  useEffect(() => {
    if (activeView === 'comparison' && selectedPair) {
      setCurrentPage(1);
    }
  }, [activeView, selectedPair]);

  // Fetch page differences
  useEffect(() => {
    // Skip if no necessary data or if not in comparison view
    if (!comparisonId || !selectedPair || activeView !== 'comparison') {
      return;
    }
    
    // Create a request ID to track this specific request
    const requestId = `page_${currentPage}_${Date.now()}`;
    let isCurrentRequest = true;
    
    const fetchDifferences = async () => {
      // Skip if already fetching
      if (fetchingRef.current) return;
      
      try {
        fetchingRef.current = true;
        console.log(`Fetching differences for page ${currentPage}...`);
        
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          currentPage,
          state.filters
        );
        
        // Only update state if this is still the current request
        if (!isCurrentRequest) {
          console.log("Ignoring stale response for page", currentPage);
          return;
        }
        
        if (details) {
          console.log("Received difference details:", details);
          
          // Store base and compare differences
          setBaseDifferences(details.baseDifferences || []);
          setCompareDifferences(details.compareDifferences || []);
          
          // Store the page metadata for proper coordinate scaling
          setPageMetadata({
            baseWidth: details.baseWidth,
            baseHeight: details.baseHeight,
            compareWidth: details.compareWidth,
            compareHeight: details.compareHeight
          });
          
          console.log("Page dimensions:", {
            baseWidth: details.baseWidth,
            baseHeight: details.baseHeight,
            compareWidth: details.compareWidth,
            compareHeight: details.compareHeight
          });
        }
      } catch (err) {
        // Only show error if this is the current request
        if (isCurrentRequest) {
          console.error("Error fetching differences:", err);
          setError("Failed to load differences: " + err.message);
        }
      } finally {
        if (isCurrentRequest) {
          fetchingRef.current = false;
        }
      }
    };

    fetchDifferences();
    
    // Cleanup function
    return () => {
      isCurrentRequest = false;
    };
  }, [comparisonId, currentPage, state.selectedDocumentPairIndex, state.filters, activeView, selectedPair]);

  // Handle document pair selection
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("Selecting document pair:", pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    setCurrentPage(1); // Reset to page 1
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
  
  // Handle adjustment parameter changes
  const updateAdjustmentParam = (param, value) => {
    setAdjustmentParams(prev => ({
      ...prev,
      [param]: value
    }));
    
    // Log the updated adjustment parameters for reference
    console.log(`Updated adjustment parameter: ${param} = ${value}`);
  };
  
  // Toggle adjustment controls visibility
  const toggleAdjustmentControls = () => {
    setShowAdjustmentControls(!showAdjustmentControls);
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
          
          {/* Calibration button - only visible in development mode */}
          {process.env.NODE_ENV === 'development' && (
            <button 
              onClick={toggleAdjustmentControls}
              style={{
                marginLeft: '10px',
                fontSize: '12px',
                padding: '3px 8px',
                background: showAdjustmentControls ? '#4caf50' : '#2c6dbd',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              {showAdjustmentControls ? 'Hide Calibration' : 'Calibrate Highlights'}
            </button>
          )}
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
      
      {/* Adjustment controls for fine-tuning highlight positions */}
      {showAdjustmentControls && (
        <div className="adjustment-controls" style={{
          padding: '10px',
          backgroundColor: '#f5f5f5',
          border: '1px solid #ddd',
          borderRadius: '4px',
          margin: '10px',
          display: 'flex',
          flexWrap: 'wrap',
          gap: '20px',
          alignItems: 'center'
        }}>
          <div>
            <label style={{ marginRight: '8px', fontWeight: 'bold' }}>X Offset:</label>
            <input 
              type="range" 
              min="-200" 
              max="200" 
              step="1"
              value={adjustmentParams.xOffset}
              onChange={(e) => updateAdjustmentParam('xOffset', parseInt(e.target.value))}
              style={{ width: '100px', verticalAlign: 'middle' }}
            />
            <span style={{ marginLeft: '8px', minWidth: '30px', display: 'inline-block' }}>
              {adjustmentParams.xOffset}px
            </span>
          </div>
          
          <div>
            <label style={{ marginRight: '8px', fontWeight: 'bold' }}>Y Offset:</label>
            <input 
              type="range" 
              min="-1000" 
              max="1000" 
              step="1"
              value={adjustmentParams.yOffset}
              onChange={(e) => updateAdjustmentParam('yOffset', parseInt(e.target.value))}
              style={{ width: '100px', verticalAlign: 'middle' }}
            />
            <span style={{ marginLeft: '8px', minWidth: '30px', display: 'inline-block' }}>
              {adjustmentParams.yOffset}px
            </span>
          </div>
          
          <div>
            <label style={{ marginRight: '8px', fontWeight: 'bold' }}>Scale:</label>
            <input 
              type="range" 
              min="0.0" 
              max="1.9" 
              step="0.001"
              value={adjustmentParams.scaleAdjustment}
              onChange={(e) => updateAdjustmentParam('scaleAdjustment', parseFloat(e.target.value))}
              style={{ width: '100px', verticalAlign: 'middle' }}
            />
            <span style={{ marginLeft: '8px', minWidth: '50px', display: 'inline-block' }}>
              {adjustmentParams.scaleAdjustment.toFixed(2)}x
            </span>
          </div>
          
          <div>
            <label style={{ marginRight: '8px', fontWeight: 'bold' }}>Flip Y:</label>
            <input 
              type="checkbox" 
              checked={adjustmentParams.flipY}
              onChange={(e) => updateAdjustmentParam('flipY', e.target.checked)}
              style={{ verticalAlign: 'middle' }}
            />
            <span style={{ marginLeft: '8px' }}>
              {adjustmentParams.flipY ? 'Enabled' : 'Disabled'}
            </span>
          </div>
          
          <button
            onClick={() => setAdjustmentParams({ 
              xOffset: 0, 
              yOffset: 0, 
              scaleAdjustment: 1.0,
              flipY: false
            })}
            style={{
              padding: '4px 8px',
              backgroundColor: '#f44336',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Reset
          </button>
          
          <div style={{ flexGrow: 1, textAlign: 'right', fontSize: '12px', color: '#666' }}>
            These controls help fine-tune highlight positioning. Find the best values, then update code.
          </div>
        </div>
      )}
      
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
                    isBaseDocument={true}
                    pageMetadata={pageMetadata}
                    xOffsetAdjustment={adjustmentParams.xOffset}
                    yOffsetAdjustment={adjustmentParams.yOffset}
                    scaleAdjustment={adjustmentParams.scaleAdjustment}
                    flipY={adjustmentParams.flipY}
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
                    isBaseDocument={false}
                    pageMetadata={pageMetadata}
                    xOffsetAdjustment={adjustmentParams.xOffset}
                    yOffsetAdjustment={adjustmentParams.yOffset}
                    scaleAdjustment={adjustmentParams.scaleAdjustment}
                    flipY={adjustmentParams.flipY}
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