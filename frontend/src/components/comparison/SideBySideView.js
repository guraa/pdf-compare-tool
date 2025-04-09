import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import EnhancedDiffHighlighter from './EnhancedDiffHighlighter';
import EnhancedDifferenceList from './EnhancedDifferenceList';
import Spinner from '../common/Spinner';
import { getDocumentPairs, getDocumentPageDetails } from '../../services/api';
import './SideBySideView.css';

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
    setSelectedDocumentPairIndex,
    setSelectedPage,
    setSelectedDifference
  } = useComparison();
  
  const [activeView, setActiveView] = useState('matching');
  const [selectedPairIndex, setSelectedPairIndex] = useState(null);
  const [selectedPair, setSelectedPair] = useState(null);
  const [localDocumentPairs, setLocalDocumentPairs] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDetails, setPageDetails] = useState(null);
  const [baseImageUrl, setBaseImageUrl] = useState(null);
  const [compareImageUrl, setCompareImageUrl] = useState(null);
  const [loadingImages, setLoadingImages] = useState(false);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);

  // Clean up blob URLs when unmounting
  useEffect(() => {
    return () => {
      isMounted.current = false;
      
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
    };
  }, [baseImageUrl, compareImageUrl]);

  // Fetch document pairs and page details
  useEffect(() => {
    const fetchDocumentPairs = async () => {
      if (!comparisonId || !isMounted.current || fetchInProgress.current || pairsAlreadyFetched.current) {
        return;
      }
      
      try {
        fetchInProgress.current = true;
        setLoading(true);
        
        const pairs = await getDocumentPairs(comparisonId);
        
        if (!isMounted.current) return;
        
        if (pairs && pairs.length > 0) {
          setLocalDocumentPairs(pairs);
          setDocumentPairs(pairs);
          
          pairsAlreadyFetched.current = true;
          
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
        console.error("Error fetching document pairs:", err);
        setError("Failed to load document pairs: " + err.message);
        setLoading(false);
      } finally {
        fetchInProgress.current = false;
      }
    };
    
    fetchDocumentPairs();
  }, [comparisonId]);

  // Fetch page details when page or pair changes
  useEffect(() => {
    const fetchPageDetails = async () => {
      if (!comparisonId || !selectedPair) return;
      
      try {
        setLoadingImages(true);
        
        // Calculate actual page numbers based on the document pair
        const basePage = selectedPair.baseStartPage + currentPage - 1;
        const comparePage = selectedPair.compareStartPage + currentPage - 1;
        
        // Clean up previous blob URLs
        if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
        if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
        
        // Reset URLs
        setBaseImageUrl(null);
        setCompareImageUrl(null);
        
        // Fetch page details with a 2-minute timeout
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 120000); // 2 minutes
        
        try {
          const details = await getDocumentPageDetails(
            comparisonId, 
            state.selectedDocumentPairIndex, 
            currentPage,
            state.filters
          );
          
          clearTimeout(timeoutId);
          
          // Log page details for debugging
          console.log('Page details from API:', details);
          
 
          // Load base document page with a 2-minute timeout
          if (basePage <= selectedPair.baseEndPage) {
            const baseResponse = await fetch(`/api/pdfs/document/${state.baseFile?.fileId}/page/${basePage}`, {
              signal: controller.signal
            });
            if (baseResponse.ok) {
              const baseBlob = await baseResponse.blob();
              const baseUrl = URL.createObjectURL(baseBlob);
              setBaseImageUrl(baseUrl);
            }
          }
          
          // Load compare document page with a 2-minute timeout
          if (comparePage <= selectedPair.compareEndPage) {
            const compareResponse = await fetch(`/api/pdfs/document/${state.compareFile?.fileId}/page/${comparePage}`, {
              signal: controller.signal
            });
            if (compareResponse.ok) {
              const compareBlob = await compareResponse.blob();
              const compareUrl = URL.createObjectURL(compareBlob);
              setCompareImageUrl(compareUrl);
            }
          }
        } catch (error) {
          clearTimeout(timeoutId);
          throw error;
        }
        
        setLoadingImages(false);
      } catch (error) {
        console.error("Error loading page data:", error);
        setLoadingImages(false);
      }
    };
    
    fetchPageDetails();
  }, [comparisonId, selectedPair, currentPage, state.selectedDocumentPairIndex]);

  // Navigation and UI handlers
  const handleSelectDocumentPair = (pairIndex, pair) => {
    if (!pair) return;
    
    setSelectedPairIndex(pairIndex);
    setSelectedPair(pair);
    setSelectedDocumentPairIndex(pairIndex);
    
    if (pair.matched) {
      setActiveView('comparison');
      setCurrentPage(1);
    }
  };

  const backToMatching = useCallback(() => {
    setActiveView('matching');
    
    if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
    if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
  }, [baseImageUrl, compareImageUrl]);

  const getMaxPageCount = useCallback(() => {
    if (!selectedPair) return 1;
    
    const basePages = selectedPair.baseEndPage - selectedPair.baseStartPage + 1;
    const comparePages = selectedPair.compareEndPage - selectedPair.compareStartPage + 1;
    
    return Math.max(basePages, comparePages);
  }, [selectedPair]);

  const goToPreviousPage = useCallback(() => {
    if (currentPage > 1) {
      setCurrentPage(prev => prev - 1);
      setSelectedPage(currentPage - 1);
    }
  }, [currentPage, setSelectedPage]);

  const goToNextPage = useCallback(() => {
    const maxPages = getMaxPageCount();
    if (currentPage < maxPages) {
      setCurrentPage(prev => prev + 1);
      setSelectedPage(currentPage + 1);
    }
  }, [currentPage, getMaxPageCount, setSelectedPage]);

  const handleDifferenceSelect = useCallback((difference) => {
    setSelectedDifference(difference);
  }, [setSelectedDifference]);

  const toggleDifferencePanel = useCallback(() => {
    setShowDifferencePanel(prev => !prev);
  }, []);

  // Render logic
  if (activeView === 'matching') {
    return (
      <DocumentMatchingView 
        comparisonId={comparisonId}
        onSelectDocumentPair={handleSelectDocumentPair}
        documentPairs={localDocumentPairs}
        loading={state.loading || fetchInProgress.current}
        error={state.error}
      />
    );
  }

  return (
    <div className="pdf-comparison-container">
      <div className="comparison-header">
        <button 
          className="back-button"
          onClick={backToMatching}
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
      </div>
      
      <div className="comparison-content">
        <div className={`documents-container ${showDifferencePanel ? 'with-panel' : ''}`}>
          {loadingImages ? (
            <div className="loading-container">
              <Spinner size="large" />
              <p>Loading comparison...</p>
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
        <div className="page-navigation">
          <button 
            onClick={goToPreviousPage}
            disabled={currentPage <= 1 || loadingImages}
          >
            Previous Page
          </button>
          
          <div className="page-indicator">
            Page {currentPage} of {getMaxPageCount()}
          </div>
          
          <button 
            onClick={goToNextPage}
            disabled={currentPage >= getMaxPageCount() || loadingImages}
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
              <option value="style">Styles Only</option>
              <option value="none">No Highlights</option>
            </select>
          </div>
        </div>
        
        {selectedPair && (
          <div className="similarity-info">
            Similarity: {Math.round(selectedPair.similarityScore * 100)}%
          </div>
        )}
      </div>
    </div>
  );
};

export default SideBySideView;
