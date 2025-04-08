import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import Spinner from '../common/Spinner';
import { getDocumentPairs } from '../../services/api';
import './SideBySideView.css';

/**
 * Radical Bypass Solution
 * This version completely bypasses both data loading AND the problematic SideBySideView component
 * by implementing a bare-minimum PDF viewer directly
 */
const SideBySideView = ({ comparisonId }) => {
  // Reference to track if component is mounted
  const isMounted = useRef(true);
  const fetchInProgress = useRef(false);
  const pairsAlreadyFetched = useRef(false);
  
  const { 
    state, 
    setError,
    setLoading,
    setDocumentPairs,
    setSelectedDocumentPairIndex
  } = useComparison();
  
  const [activeView, setActiveView] = useState('matching');
  const [selectedPairIndex, setSelectedPairIndex] = useState(null);
  const [selectedPair, setSelectedPair] = useState(null);
  const [localDocumentPairs, setLocalDocumentPairs] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [loadingImages, setLoadingImages] = useState(false);
  const [baseImageUrl, setBaseImageUrl] = useState(null);
  const [compareImageUrl, setCompareImageUrl] = useState(null);
  
  // Set isMounted to false when component unmounts
  useEffect(() => {
    return () => {
      isMounted.current = false;
      
      // Clean up any blob URLs when unmounting
      if (baseImageUrl) {
        URL.revokeObjectURL(baseImageUrl);
      }
      if (compareImageUrl) {
        URL.revokeObjectURL(compareImageUrl);
      }
    };
  }, [baseImageUrl, compareImageUrl]);
  
  // Log when view changes
  useEffect(() => {
    console.log("🔍 Active view changed to:", activeView);
    
    // Load PDF images directly when view changes to comparison
    if (activeView === 'comparison' && selectedPair) {
      loadPageImages(1);
    }
  }, [activeView, selectedPair]);
  
  // Load PDF page images directly
  const loadPageImages = async (pageNum) => {
    if (!selectedPair) return;
    
    try {
      setLoadingImages(true);
      
      // Calculate actual page numbers based on the document pair
      const basePage = selectedPair.baseStartPage + pageNum - 1;
      const comparePage = selectedPair.compareStartPage + pageNum - 1;
      
      console.log(`Loading page images for base page ${basePage} and compare page ${comparePage}`);
      
      // Clean up previous blob URLs
      if (baseImageUrl) {
        URL.revokeObjectURL(baseImageUrl);
      }
      if (compareImageUrl) {
        URL.revokeObjectURL(compareImageUrl);
      }
      
      // Reset URLs
      setBaseImageUrl(null);
      setCompareImageUrl(null);
      
      // Load base document page
      if (basePage <= selectedPair.baseEndPage) {
        try {
          const baseResponse = await fetch(`/api/pdfs/document/${state.baseFile?.fileId}/page/${basePage}`);
          if (baseResponse.ok) {
            const baseBlob = await baseResponse.blob();
            const baseUrl = URL.createObjectURL(baseBlob);
            setBaseImageUrl(baseUrl);
          }
        } catch (error) {
          console.error("Error loading base page:", error);
        }
      }
      
      // Load compare document page
      if (comparePage <= selectedPair.compareEndPage) {
        try {
          const compareResponse = await fetch(`/api/pdfs/document/${state.compareFile?.fileId}/page/${comparePage}`);
          if (compareResponse.ok) {
            const compareBlob = await compareResponse.blob();
            const compareUrl = URL.createObjectURL(compareBlob);
            setCompareImageUrl(compareUrl);
          }
        } catch (error) {
          console.error("Error loading compare page:", error);
        }
      }
      
      // Update current page
      setCurrentPage(pageNum);
      
    } catch (error) {
      console.error("Error loading page images:", error);
    } finally {
      setLoadingImages(false);
    }
  };
  
  // Fetch document pairs when component mounts
  useEffect(() => {
    const fetchDocumentPairs = async () => {
      if (!comparisonId || !isMounted.current || fetchInProgress.current || pairsAlreadyFetched.current) {
        return;
      }
      
      try {
        fetchInProgress.current = true;
        setLoading(true);
        
        console.log(`Fetching document pairs for comparison: ${comparisonId}`);
        const pairs = await getDocumentPairs(comparisonId);
        
        // Check if component is still mounted
        if (!isMounted.current) return;
        
        if (pairs && pairs.length > 0) {
          console.log(`Received ${pairs.length} document pairs`);
          
          // Update state
          setLocalDocumentPairs(pairs);
          setDocumentPairs(pairs);
          
          // Set flag to prevent refetching
          pairsAlreadyFetched.current = true;
          
          // Auto-select the first matched pair if available
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          if (matchedPairIndex >= 0) {
            setSelectedPairIndex(matchedPairIndex);
            setSelectedDocumentPairIndex(matchedPairIndex);
            setSelectedPair(pairs[matchedPairIndex]);
          } else {
            setSelectedPairIndex(0);
            setSelectedDocumentPairIndex(0);
            setSelectedPair(pairs[0]);
          }
          
          setLoading(false);
        } else {
          throw new Error("No document pairs returned");
        }
      } catch (err) {
        // Handle errors...
        console.error("Error fetching document pairs:", err);
        setError("Failed to load document pairs: " + err.message);
        setLoading(false);
      } finally {
        fetchInProgress.current = false;
      }
    };
    
    fetchDocumentPairs();
  }, [comparisonId, setDocumentPairs, setLoading, setError, setSelectedDocumentPairIndex]);
  
  // View comparison button handler
  const viewPairComparison = useCallback(() => {
    console.log("⚡ viewPairComparison called directly");
    
    if (!selectedPair) {
      console.error("No pair selected");
      return;
    }
    
    // Switch to comparison view immediately
    setActiveView('comparison');
    
    // Reset current page to 1
    setCurrentPage(1);
  }, [selectedPair]);
  
  // Handle selecting a document pair
  const handleSelectDocumentPair = (pairIndex, pair) => {
    console.log("🔵 handleSelectDocumentPair called with", pairIndex, pair);
    
    if (!pair) {
      console.error("Invalid pair selected");
      return;
    }
    
    // Update the global context and local state for the selected pair
    setSelectedPairIndex(pairIndex);
    setSelectedPair(pair);
    setSelectedDocumentPairIndex(pairIndex);
    
    // If this came from the "View Comparison" button, switch the view immediately
    if (pair.matched) {
      console.log("📱 Valid matched pair selected - switching to comparison view");
      setActiveView('comparison');
      
      // Reset current page when changing pairs
      setCurrentPage(1);
    }
  };
  
  // Function to go back to document matching view
  const backToMatching = useCallback(() => {
    console.log("◀️ Going back to matching view");
    setActiveView('matching');
    
    // Clean up any blob URLs when going back
    if (baseImageUrl) {
      URL.revokeObjectURL(baseImageUrl);
      setBaseImageUrl(null);
    }
    if (compareImageUrl) {
      URL.revokeObjectURL(compareImageUrl);
      setCompareImageUrl(null);
    }
  }, [baseImageUrl, compareImageUrl]);

  // Get the max page count for the current document pair
  const getMaxPageCount = useCallback(() => {
    if (!selectedPair) return 1;
    
    const basePages = selectedPair.baseEndPage - selectedPair.baseStartPage + 1;
    const comparePages = selectedPair.compareEndPage - selectedPair.compareStartPage + 1;
    
    return Math.max(basePages, comparePages);
  }, [selectedPair]);

  // Navigate to the previous page
  const goToPreviousPage = useCallback(() => {
    if (currentPage > 1) {
      loadPageImages(currentPage - 1);
    }
  }, [currentPage]);

  // Navigate to the next page
  const goToNextPage = useCallback(() => {
    const maxPages = getMaxPageCount();
    if (currentPage < maxPages) {
      loadPageImages(currentPage + 1);
    }
  }, [currentPage, getMaxPageCount]);

  console.log("🔄 Rendering with activeView:", activeView, 
              "selectedPairIndex:", selectedPairIndex, 
              "hasSelectedPair:", !!selectedPair);

  return (
    <div className="smart-comparison-container">
      {activeView === 'matching' ? (
        <>
          <DocumentMatchingView 
            comparisonId={comparisonId}
            onSelectDocumentPair={handleSelectDocumentPair}
            documentPairs={localDocumentPairs}
            loading={state.loading || fetchInProgress.current}
            error={state.error}
          />
          
          {/* Floating action bar for viewing comparison */}
          {selectedPair && selectedPair.matched && (
            <div className="view-comparison-bar">
              <button 
                className="view-comparison-button"
                onClick={viewPairComparison}
                style={{ cursor: 'pointer' }}
              >
                View Comparison for Document {selectedPairIndex + 1}
              </button>
            </div>
          )}
        </>
      ) : (
        <div className="comparison-view">
          <div className="comparison-header">
            <button 
              className="back-button"
              onClick={backToMatching}
              style={{ cursor: 'pointer' }}
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
              </svg>
              Back to Document List
            </button>
            
            <div className="document-info">
              {selectedPair && (
                <>
                  <h3>Document {selectedPairIndex + 1} Comparison</h3>
                  <div className="page-ranges">
                    <span className="base-range">Base: {selectedPair.baseStartPage}-{selectedPair.baseEndPage}</span>
                    <span className="separator">vs</span>
                    <span className="compare-range">Compare: {selectedPair.compareStartPage}-{selectedPair.compareEndPage}</span>
                  </div>
                </>
              )}
            </div>
            
            <div className="document-navigation">
              <button 
                className="nav-button prev"
                onClick={() => {
                  if (selectedPairIndex > 0) {
                    const newIndex = selectedPairIndex - 1;
                    handleSelectDocumentPair(newIndex, localDocumentPairs[newIndex]);
                  }
                }}
                disabled={selectedPairIndex <= 0}
                style={{ cursor: selectedPairIndex > 0 ? 'pointer' : 'not-allowed' }}
              >
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
                </svg>
                Previous Document
              </button>
              
              <div className="document-counter">
                {selectedPairIndex + 1} / {localDocumentPairs.length}
              </div>
              
              <button 
                className="nav-button next"
                onClick={() => {
                  if (selectedPairIndex < localDocumentPairs.length - 1) {
                    const newIndex = selectedPairIndex + 1;
                    handleSelectDocumentPair(newIndex, localDocumentPairs[newIndex]);
                  }
                }}
                disabled={selectedPairIndex >= localDocumentPairs.length - 1}
                style={{ cursor: selectedPairIndex < localDocumentPairs.length - 1 ? 'pointer' : 'not-allowed' }}
              >
                Next Document
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
                </svg>
              </button>
            </div>
          </div>
          
          <div className="emergency-mode-banner" style={{
            backgroundColor: '#fffde7',
            borderBottom: '1px solid #ffd600',
            padding: '8px 16px',
            fontSize: '14px',
            color: '#7e57c2',
            textAlign: 'center'
          }}>
            🛠️ Basic document viewer mode - No difference highlighting available
          </div>
          
          {/* Custom direct PDF viewer */}
          <div style={{ display: 'flex', flexDirection: 'column', padding: '16px' }}>
            {/* Page navigation */}
            <div style={{ 
              display: 'flex', 
              justifyContent: 'center', 
              alignItems: 'center', 
              margin: '0 0 16px 0',
              gap: '16px'
            }}>
              <button 
                onClick={goToPreviousPage}
                disabled={currentPage <= 1 || loadingImages}
                style={{
                  padding: '8px 16px',
                  backgroundColor: currentPage <= 1 ? '#e0e0e0' : '#2c6dbd',
                  color: currentPage <= 1 ? '#9e9e9e' : 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: currentPage <= 1 ? 'not-allowed' : 'pointer'
                }}
              >
                Previous Page
              </button>
              
              <div style={{ fontWeight: 'bold' }}>
                Page {currentPage} of {getMaxPageCount()}
              </div>
              
              <button 
                onClick={goToNextPage}
                disabled={currentPage >= getMaxPageCount() || loadingImages}
                style={{
                  padding: '8px 16px',
                  backgroundColor: currentPage >= getMaxPageCount() ? '#e0e0e0' : '#2c6dbd',
                  color: currentPage >= getMaxPageCount() ? '#9e9e9e' : 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: currentPage >= getMaxPageCount() ? 'not-allowed' : 'pointer'
                }}
              >
                Next Page
              </button>
            </div>
            
            {/* Document viewers */}
            <div style={{ 
              display: 'flex', 
              justifyContent: 'center', 
              gap: '16px', 
              flexWrap: 'wrap'
            }}>
              {/* Base document */}
              <div style={{ 
                border: '1px solid #e0e0e0', 
                borderRadius: '4px', 
                padding: '8px',
                backgroundColor: '#f5f5f5',
                width: '45%',
                minWidth: '300px',
                maxWidth: '700px'
              }}>
                <div style={{ 
                  borderBottom: '1px solid #e0e0e0', 
                  padding: '8px 0', 
                  marginBottom: '8px',
                  fontWeight: 'bold',
                  color: '#2c6dbd'
                }}>
                  Base Document
                </div>
                
                {loadingImages && !baseImageUrl ? (
                  <div style={{ 
                    display: 'flex', 
                    justifyContent: 'center', 
                    alignItems: 'center',
                    height: '300px'
                  }}>
                    <Spinner size="large" />
                  </div>
                ) : baseImageUrl ? (
                  <img 
                    src={baseImageUrl} 
                    alt="Base document page" 
                    style={{ 
                      maxWidth: '100%', 
                      height: 'auto', 
                      display: 'block',
                      margin: '0 auto',
                      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                    }} 
                  />
                ) : (
                  <div style={{ 
                    display: 'flex', 
                    justifyContent: 'center', 
                    alignItems: 'center',
                    height: '300px',
                    backgroundColor: '#eeeeee',
                    color: '#757575',
                    fontStyle: 'italic'
                  }}>
                    Page not available
                  </div>
                )}
              </div>
              
              {/* Compare document */}
              <div style={{ 
                border: '1px solid #e0e0e0', 
                borderRadius: '4px', 
                padding: '8px',
                backgroundColor: '#f5f5f5',
                width: '45%',
                minWidth: '300px',
                maxWidth: '700px'
              }}>
                <div style={{ 
                  borderBottom: '1px solid #e0e0e0', 
                  padding: '8px 0', 
                  marginBottom: '8px',
                  fontWeight: 'bold',
                  color: '#f44336'
                }}>
                  Compare Document
                </div>
                
                {loadingImages && !compareImageUrl ? (
                  <div style={{ 
                    display: 'flex', 
                    justifyContent: 'center', 
                    alignItems: 'center',
                    height: '300px'
                  }}>
                    <Spinner size="large" />
                  </div>
                ) : compareImageUrl ? (
                  <img 
                    src={compareImageUrl} 
                    alt="Compare document page" 
                    style={{ 
                      maxWidth: '100%', 
                      height: 'auto', 
                      display: 'block',
                      margin: '0 auto',
                      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                    }} 
                  />
                ) : (
                  <div style={{ 
                    display: 'flex', 
                    justifyContent: 'center', 
                    alignItems: 'center',
                    height: '300px',
                    backgroundColor: '#eeeeee',
                    color: '#757575',
                    fontStyle: 'italic'
                  }}>
                    Page not available
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SideBySideView;