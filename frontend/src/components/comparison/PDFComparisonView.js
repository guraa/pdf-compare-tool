import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import EnhancedDiffHighlighter from './EnhancedDiffHighlighter';
import EnhancedDifferenceList from './EnhancedDifferenceList';
import { getDocumentPageDetails, getDocumentPage } from '../../services/api';
import Spinner from '../common/Spinner';
import './PDFComparisonView.css';

/**
 * PDFComparisonView component - the main view for comparing PDFs with visual highlighting
 * Inspired by i-net PDFC functionality
 */
const PDFComparisonView = ({ comparisonId, documentPair }) => {
  // Refs to track component state
  const isMounted = useRef(true);
  
  // Context for global state
  const { state, setSelectedPage, setSelectedDifference } = useComparison();
  
  // Local state 
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDetails, setPageDetails] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [baseImageUrl, setBaseImageUrl] = useState(null);
  const [compareImageUrl, setCompareImageUrl] = useState(null);
  const [highlightMode, setHighlightMode] = useState('all');
  const [totalPageCount, setTotalPageCount] = useState(0);
  const [viewMode, setViewMode] = useState('sideBySide'); // sideBySide, overlay, changes
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  
  // Update ref when component unmounts
  useEffect(() => {
    return () => {
      isMounted.current = false;
      
      // Clean up any blob URLs when unmounting
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
    };
  }, [baseImageUrl, compareImageUrl]);
  
  // Calculate total page count when documentPair changes
  useEffect(() => {
    if (!documentPair) return;
    
    // Get the max page count for this document pair
    const basePages = documentPair.baseEndPage - documentPair.baseStartPage + 1;
    const comparePages = documentPair.compareEndPage - documentPair.compareStartPage + 1;
    setTotalPageCount(Math.max(basePages, comparePages));
    
    // Reset to page 1 when switching document pairs
    setCurrentPage(1);
  }, [documentPair]);
  
  // Load page details and images when page changes
  useEffect(() => {
    if (!comparisonId || !documentPair) return;
    
    const fetchPageData = async () => {
      setLoading(true);
      setError(null);
      
      try {
        // Calculate base and compare page numbers
        const basePage = documentPair.baseStartPage + currentPage - 1;
        const comparePage = documentPair.compareStartPage + currentPage - 1;
        
        // Clean up previous image URLs
        if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
        if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
        
        // Reset URLs
        setBaseImageUrl(null);
        setCompareImageUrl(null);
        
        // Fetch difference details for this page
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          currentPage,
          state.filters
        );
        
        // Set page details
        if (isMounted.current) {
          setPageDetails(details);
        }
        
        // Load base document page
        if (basePage <= documentPair.baseEndPage) {
          try {
            const baseResponse = await getDocumentPage(state.baseFile?.fileId, basePage);
            if (isMounted.current) {
              const baseUrl = URL.createObjectURL(baseResponse);
              setBaseImageUrl(baseUrl);
            }
          } catch (error) {
            console.error("Error loading base page:", error);
          }
        }
        
        // Load compare document page
        if (comparePage <= documentPair.compareEndPage) {
          try {
            const compareResponse = await getDocumentPage(state.compareFile?.fileId, comparePage);
            if (isMounted.current) {
              const compareUrl = URL.createObjectURL(compareResponse);
              setCompareImageUrl(compareUrl);
            }
          } catch (error) {
            console.error("Error loading compare page:", error);
          }
        }
        
      } catch (error) {
        console.error("Error loading page data:", error);
        if (isMounted.current) {
          setError("Failed to load page comparison data. Please try another page.");
        }
      } finally {
        if (isMounted.current) {
          setLoading(false);
        }
      }
    };
    
    fetchPageData();
  }, [comparisonId, documentPair, currentPage, state.selectedDocumentPairIndex, state.baseFile, state.compareFile, state.filters]);
  
  // Handle page navigation
  const goToPage = useCallback((pageNum) => {
    if (pageNum >= 1 && pageNum <= totalPageCount) {
      setCurrentPage(pageNum);
      // Also update in global state
      setSelectedPage(pageNum);
    }
  }, [totalPageCount, setSelectedPage]);
  
  const goToPreviousPage = useCallback(() => {
    goToPage(currentPage - 1);
  }, [currentPage, goToPage]);
  
  const goToNextPage = useCallback(() => {
    goToPage(currentPage + 1);
  }, [currentPage, goToPage]);

  // Handle difference selection
  const handleDifferenceSelect = useCallback((difference) => {
    console.log("Selected difference:", difference);
    // Set the difference in global state
    setSelectedDifference(difference);
  }, [setSelectedDifference]);
  
  // Count differences for the active page
  const getDifferenceCount = useCallback(() => {
    if (!pageDetails) return 0;
    
    // Use a Set to avoid counting duplicates
    const uniqueIds = new Set();
    
    if (pageDetails.baseDifferences) {
      pageDetails.baseDifferences.forEach(diff => uniqueIds.add(diff.id));
    }
    
    if (pageDetails.compareDifferences) {
      pageDetails.compareDifferences.forEach(diff => uniqueIds.add(diff.id));
    }
    
    return uniqueIds.size;
  }, [pageDetails]);
  
  // Toggle difference panel visibility
  const toggleDifferencePanel = useCallback(() => {
    setShowDifferencePanel(prev => !prev);
  }, []);

  // If document pair is missing, show error message
  if (!documentPair) {
    return (
      <div className="pdf-comparison-error">
        <h3>No document pair selected</h3>
        <p>Please select a document pair to compare</p>
      </div>
    );
  }

  return (
    <div className="pdf-comparison-container">
      <div className="comparison-header">
        <div className="page-navigation">
          <button 
            onClick={goToPreviousPage} 
            disabled={currentPage <= 1 || loading}
            className="nav-button"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
            <span>Previous</span>
          </button>
          
          <div className="page-selector">
            <input 
              type="number" 
              min="1" 
              max={totalPageCount}
              value={currentPage}
              onChange={(e) => {
                const pageNum = parseInt(e.target.value);
                if (!isNaN(pageNum)) goToPage(pageNum);
              }}
            />
            <span>of {totalPageCount}</span>
          </div>
          
          <button 
            onClick={goToNextPage} 
            disabled={currentPage >= totalPageCount || loading}
            className="nav-button"
          >
            <span>Next</span>
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
            </svg>
          </button>
        </div>
        
        <div className="view-controls">
          <div className="view-mode-buttons">
            <button 
              className={`view-mode-button ${viewMode === 'sideBySide' ? 'active' : ''}`}
              onClick={() => setViewMode('sideBySide')}
              title="Side by side view"
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M3 5v14h18V5H3zm8 14H3V5h8v14zm10 0h-8V5h8v14z" />
              </svg>
            </button>
            
            <button 
              className={`view-mode-button ${viewMode === 'overlay' ? 'active' : ''}`}
              onClick={() => setViewMode('overlay')}
              title="Overlay view"
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M18 16h-2v-1h-1c-1.1 0-2-.9-2-2V9h5V8c0-1.1-.9-2-2-2h-6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h5c1.1 0 2-.9 2-2v-1c0-.55.45-1 1-1s1 .45 1 1v1z" />
                <circle cx="17" cy="12" r="1" />
              </svg>
            </button>
            
            <button 
              className={`view-mode-button ${viewMode === 'changes' ? 'active' : ''}`}
              onClick={() => setViewMode('changes')}
              title="Changes only view"
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14zm-7-2h2V7h-4v2h2z" />
              </svg>
            </button>
          </div>
          
          <div className="highlight-controls">
            <select 
              value={highlightMode}
              onChange={(e) => setHighlightMode(e.target.value)}
              title="Change highlight type"
            >
              <option value="all">All Differences</option>
              <option value="text">Text Only</option>
              <option value="image">Images Only</option>
              <option value="style">Styles Only</option>
              <option value="none">No Highlights</option>
            </select>
          </div>
          
          <div className="difference-counter">
            <span className="diff-badge" title="Differences on this page">
              {getDifferenceCount()}
            </span>
          </div>
        </div>
      </div>
      
      <div className="comparison-content">
        <div className={`documents-container ${showDifferencePanel ? 'with-panel' : ''}`}>
          {loading ? (
            <div className="loading-container">
              <Spinner size="large" />
              <p>Loading comparison...</p>
            </div>
          ) : error ? (
            <div className="error-container">
              <div className="error-icon">
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
                </svg>
              </div>
              <p>{error}</p>
            </div>
          ) : (
            <EnhancedDiffHighlighter 
              pageDetails={pageDetails}
              baseImageUrl={baseImageUrl}
              compareImageUrl={compareImageUrl}
              viewSettings={{
                highlightMode,
                zoom: 1
              }}
              onDifferenceSelect={handleDifferenceSelect}
              selectedDifference={state.selectedDifference}
            />
          )}
        </div>
        
        {showDifferencePanel && (
          <div className="difference-panel">
            <EnhancedDifferenceList 
              differences={pageDetails}
              onDifferenceSelect={handleDifferenceSelect}
              selectedDifference={state.selectedDifference}
            />
          </div>
        )}
        
        <button 
          className={`toggle-panel-button ${!showDifferencePanel ? 'hidden' : ''}`}
          onClick={toggleDifferencePanel}
          title={showDifferencePanel ? "Hide difference panel" : "Show difference panel"}
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            {showDifferencePanel ? (
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6-6-6z" />
            ) : (
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            )}
          </svg>
        </button>
      </div>
      
      <div className="comparison-footer">
        <div className="document-info">
          <div className="base-info">
            <strong>Base:</strong> Pages {documentPair.baseStartPage}-{documentPair.baseEndPage}
          </div>
          <div className="compare-info">
            <strong>Compare:</strong> Pages {documentPair.compareStartPage}-{documentPair.compareEndPage}
          </div>
        </div>
        <div className="similarity-info">
          Similarity: {Math.round(documentPair.similarityScore * 100)}%
        </div>
      </div>
    </div>
  );
};

export default PDFComparisonView;