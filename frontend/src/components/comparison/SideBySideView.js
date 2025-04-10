import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import SimplifiedDifferenceList from './DifferenceList';
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
  const [baseDifferences, setBaseDifferences] = useState([]);
  const [compareDifferences, setCompareDifferences] = useState([]);
  const [baseImageUrl, setBaseImageUrl] = useState(null);
  const [compareImageUrl, setCompareImageUrl] = useState(null);
  const [loadingImages, setLoadingImages] = useState(false);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [baseImageDimensions, setBaseImageDimensions] = useState({ width: 0, height: 0 });
  const [compareImageDimensions, setCompareImageDimensions] = useState({ width: 0, height: 0 });
  const [syncScroll, setSyncScroll] = useState(true);
  const baseContainerRef = useRef(null);
  const compareContainerRef = useRef(null);

  // Clean up blob URLs when unmounting
  useEffect(() => {
    return () => {
      isMounted.current = false;
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
    };
  }, [baseImageUrl, compareImageUrl]);

  // Handle synchronized scrolling
  useEffect(() => {
    if (!syncScroll || !baseContainerRef.current || !compareContainerRef.current) return;

    const baseContainer = baseContainerRef.current;
    const compareContainer = compareContainerRef.current;

    const handleBaseScroll = () => {
      const { scrollTop, scrollLeft, scrollHeight, clientHeight, scrollWidth, clientWidth } = baseContainer;
      
      // Calculate percentage positions
      const verticalPosition = scrollHeight <= clientHeight ? 0 : scrollTop / (scrollHeight - clientHeight);
      const horizontalPosition = scrollWidth <= clientWidth ? 0 : scrollLeft / (scrollWidth - clientWidth);
      
      // Apply to compare container
      const compareScrollHeight = compareContainer.scrollHeight - compareContainer.clientHeight;
      const compareScrollWidth = compareContainer.scrollWidth - compareContainer.clientWidth;
      
      compareContainer.scrollTop = verticalPosition * compareScrollHeight;
      compareContainer.scrollLeft = horizontalPosition * compareScrollWidth;
    };

    const handleCompareScroll = () => {
      const { scrollTop, scrollLeft, scrollHeight, clientHeight, scrollWidth, clientWidth } = compareContainer;
      
      // Calculate percentage positions
      const verticalPosition = scrollHeight <= clientHeight ? 0 : scrollTop / (scrollHeight - clientHeight);
      const horizontalPosition = scrollWidth <= clientWidth ? 0 : scrollLeft / (scrollWidth - clientWidth);
      
      // Apply to base container
      const baseScrollHeight = baseContainer.scrollHeight - baseContainer.clientHeight;
      const baseScrollWidth = baseContainer.scrollWidth - baseContainer.clientWidth;
      
      baseContainer.scrollTop = verticalPosition * baseScrollHeight;
      baseContainer.scrollLeft = horizontalPosition * baseScrollWidth;
    };

    baseContainer.addEventListener('scroll', handleBaseScroll);
    compareContainer.addEventListener('scroll', handleCompareScroll);

    return () => {
      baseContainer.removeEventListener('scroll', handleBaseScroll);
      compareContainer.removeEventListener('scroll', handleCompareScroll);
    };
  }, [syncScroll, baseImageDimensions, compareImageDimensions]);

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
  }, [comparisonId, setLoading, setError, setDocumentPairs, setSelectedDocumentPairIndex]);

  // Fetch page details and images when page or pair changes
  useEffect(() => {
    let isComponentMounted = true;
    const controller = new AbortController();
    const { signal } = controller;

    const fetchPageData = async () => {
      if (!comparisonId || !selectedPair || !state.baseFile?.fileId || !state.compareFile?.fileId) {
        return;
      }

      setLoadingImages(true);
      setPageDetails(null);
      setBaseDifferences([]);
      setCompareDifferences([]);

      // Clean up previous blob URLs before fetching new ones
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
      setBaseImageUrl(null);
      setCompareImageUrl(null);

      try {
        // 1. Fetch Page Details
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          currentPage,
          state.filters,
          signal
        );

        if (!isComponentMounted) return;
        
        setPageDetails(details);
        
        // Process differences
        if (details) {
          setBaseDifferences(details.baseDifferences || []);
          setCompareDifferences(details.compareDifferences || []);
        }

        // 2. Fetch Images Concurrently
        const basePage = selectedPair.baseStartPage + currentPage - 1;
        const comparePage = selectedPair.compareStartPage + currentPage - 1;

        const fetchImage = async (fileId, pageNum, docType) => {
          if (!fileId || pageNum < 1) return null;

          try {
            const response = await fetch(`/api/pdfs/document/${fileId}/page/${pageNum}`, { signal });
            if (!response.ok) {
              throw new Error(`Failed to fetch ${docType} page ${pageNum}: ${response.statusText}`);
            }
            const blob = await response.blob();
            return URL.createObjectURL(blob);
          } catch (error) {
            if (error.name !== 'AbortError') {
              console.error(`Error fetching ${docType} image (page ${pageNum}):`, error);
            }
            return null;
          }
        };

        const [baseResult, compareResult] = await Promise.allSettled([
          basePage <= selectedPair.baseEndPage ? fetchImage(state.baseFile.fileId, basePage, 'base') : Promise.resolve(null),
          comparePage <= selectedPair.compareEndPage ? fetchImage(state.compareFile.fileId, comparePage, 'compare') : Promise.resolve(null)
        ]);

        if (!isComponentMounted) {
          // Revoke URLs if component unmounted during fetch
          if (baseResult.status === 'fulfilled' && baseResult.value) URL.revokeObjectURL(baseResult.value);
          if (compareResult.status === 'fulfilled' && compareResult.value) URL.revokeObjectURL(compareResult.value);
          return;
        }

        // 3. Update Image States
        const newBaseUrl = baseResult.status === 'fulfilled' ? baseResult.value : null;
        const newCompareUrl = compareResult.status === 'fulfilled' ? compareResult.value : null;

        setBaseImageUrl(newBaseUrl);
        setCompareImageUrl(newCompareUrl);

        if (baseResult.status === 'rejected') setError(`Failed to load base image: ${baseResult.reason?.message || 'Unknown error'}`);
        if (compareResult.status === 'rejected') setError(`Failed to load compare image: ${compareResult.reason?.message || 'Unknown error'}`);

      } catch (error) {
        if (error.name !== 'AbortError' && isComponentMounted) {
          console.error("Error loading page data:", error);
          setError(`Failed to load page data: ${error.message}`);
          setPageDetails(null);
          setBaseImageUrl(null);
          setCompareImageUrl(null);
        }
      } finally {
        if (isComponentMounted) {
          setLoadingImages(false);
        }
      }
    };

    fetchPageData();

    return () => {
      isComponentMounted = false;
      controller.abort();
    };
  }, [
    comparisonId, 
    selectedPair, 
    currentPage, 
    state.selectedDocumentPairIndex, 
    state.filters, 
    state.baseFile?.fileId,
    state.compareFile?.fileId,
    setError,
    baseImageUrl,
    compareImageUrl
  ]);

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

  const handleBaseImageLoaded = (width, height) => {
    setBaseImageDimensions({ width, height });
  };

  const handleCompareImageLoaded = (width, height) => {
    setCompareImageDimensions({ width, height });
  };

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
          <div className="document-view base-document" ref={baseContainerRef}>
            <div className="document-header">
              <h4>Base Document</h4>
              {selectedPair && (
                <span className="page-info">
                  Page {currentPage} (Document page {selectedPair.baseStartPage + currentPage - 1})
                </span>
              )}
            </div>
            <div className="document-content">
              {loadingImages ? (
                <div className="loading-container">
                  <Spinner size="medium" />
                  <p>Loading base document...</p>
                </div>
              ) : baseImageUrl ? (
                <PDFRenderer 
                  fileId={state.baseFile?.fileId}
                  page={selectedPair ? selectedPair.baseStartPage + currentPage - 1 : 1}
                  zoom={1}
                  highlightMode={highlightMode !== 'none' ? highlightMode : null}
                  differences={baseDifferences}
                  selectedDifference={state.selectedDifference}
                  onDifferenceSelect={handleDifferenceSelect}
                  onImageLoaded={handleBaseImageLoaded}
                  interactive={true}
                />
              ) : (
                <div className="no-content">
                  <p>No base document page available</p>
                </div>
              )}
            </div>
          </div>
          
          <div className="document-view compare-document" ref={compareContainerRef}>
            <div className="document-header">
              <h4>Compare Document</h4>
              {selectedPair && (
                <span className="page-info">
                  Page {currentPage} (Document page {selectedPair.compareStartPage + currentPage - 1})
                </span>
              )}
            </div>
            <div className="document-content">
              {loadingImages ? (
                <div className="loading-container">
                  <Spinner size="medium" />
                  <p>Loading compare document...</p>
                </div>
              ) : compareImageUrl ? (
                <PDFRenderer 
                  fileId={state.compareFile?.fileId}
                  page={selectedPair ? selectedPair.compareStartPage + currentPage - 1 : 1}
                  zoom={1}
                  highlightMode={highlightMode !== 'none' ? highlightMode : null}
                  differences={compareDifferences}
                  selectedDifference={state.selectedDifference}
                  onDifferenceSelect={handleDifferenceSelect}
                  onImageLoaded={handleCompareImageLoaded}
                  interactive={true}
                />
              ) : (
                <div className="no-content">
                  <p>No compare document page available</p>
                </div>
              )}
            </div>
          </div>
        </div>
        
        {showDifferencePanel && (
          <div className="difference-panel">
            <SimplifiedDifferenceList 
              result={pageDetails}
              onDifferenceClick={handleDifferenceSelect}
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
          <div className="sync-scroll-control">
            <label>
              <input 
                type="checkbox" 
                checked={syncScroll} 
                onChange={() => setSyncScroll(!syncScroll)}
              />
              Sync Scroll
            </label>
          </div>
          
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