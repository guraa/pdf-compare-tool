import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { getDocumentPageDetails, getDocumentPage } from '../../services/api';
import EnhancedDifferenceList from './EnhancedDifferenceList';
import Spinner from '../common/Spinner';
import './SideBySideView.css';

// Simple PDF page component with improved error handling and debugging
const PDFPage = ({ imageUrl, differences = [], onSelectDifference, selectedDifference }) => {
  const [imageError, setImageError] = useState(false);
  const [imageLoaded, setImageLoaded] = useState(false);
  
  // Debug output for component props
  console.log(`PDFPage: Rendering with imageUrl=${imageUrl ? 'present' : 'missing'}, differences=${differences.length}`);

  // Reset state when imageUrl changes
  useEffect(() => {
    console.log(`PDFPage: imageUrl changed to ${imageUrl ? imageUrl.substring(0, 30) + '...' : 'null'}`);
    setImageError(false);
    setImageLoaded(false);
  }, [imageUrl]);

  // Debug output for component state
  useEffect(() => {
    console.log(`PDFPage: State updated - imageError=${imageError}, imageLoaded=${imageLoaded}`);
  }, [imageError, imageLoaded]);

  const handleImageLoad = () => {
    console.log("PDFPage: Image loaded successfully");
    setImageLoaded(true);
  };

  const handleImageError = (e) => {
    console.error("PDFPage: Image loading error:", e);
    setImageError(true);
  };

  return (
    <div className="pdf-page">
      {imageUrl ? (
        <div className="pdf-image-container">
          {!imageError ? (
            <>
              <img 
                src={imageUrl} 
                alt="PDF page" 
                className="pdf-image"
                style={{ display: imageLoaded ? 'block' : 'none' }}
                onLoad={handleImageLoad}
                onError={handleImageError}
              />
              {!imageLoaded && !imageError && (
                <div className="pdf-placeholder">
                  <Spinner size="small" />
                  <p>Loading image...</p>
                </div>
              )}
            </>
          ) : (
            <div className="pdf-error-placeholder">
              <p>Failed to load image</p>
              <p>URL: {imageUrl ? imageUrl.substring(0, 30) + '...' : 'null'}</p>
              <p>Please try refreshing the page</p>
            </div>
          )}
          
          {imageLoaded && !imageError && (
            <div className="highlight-layer">
              {differences && differences.map((diff) => (
                <div 
                  key={diff.id}
                  className={`highlight-box ${diff.type || ''} ${diff.changeType || ''} ${selectedDifference?.id === diff.id ? 'selected' : ''}`}
                  style={{
                    left: diff.position?.x || 0,
                    top: diff.position?.y || 0,
                    width: diff.bounds?.width || 100,
                    height: diff.bounds?.height || 30
                  }}
                  onClick={() => onSelectDifference(diff)}
                  title={diff.description || `${diff.type} ${diff.changeType}`}
                />
              ))}
            </div>
          )}
        </div>
      ) : (
        <div className="pdf-placeholder">
          <Spinner size="small" />
          <p>Waiting for image URL...</p>
        </div>
      )}
    </div>
  );
};

// Main component
const SideBySideView = ({ comparisonId, result, baseFileId, compareFileId, selectedPairIndexProp = 0 }) => {
  // Refs for cleanup
  const isMounted = useRef(true);
  
  // Context
  const { 
    state, 
    setSelectedPage, 
    setSelectedDifference
  } = useComparison();
  
  // Component state
  const [pageDetails, setPageDetails] = useState(null);
  const [baseImageUrl, setBaseImageUrl] = useState(null);
  const [compareImageUrl, setCompareImageUrl] = useState(null);
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(state.selectedPage || 1);
  
  // Get document pair
  const documentPair = result?.documentPairs?.[selectedPairIndexProp] || null;
  
  // Calculate page counts and make sure we have a valid selected page
  const basePageCount = documentPair?.basePageCount || result?.basePageCount || 0;
  const comparePageCount = documentPair?.comparePageCount || result?.comparePageCount || 0;
  const totalPages = Math.max(basePageCount, comparePageCount);
  
  // Ensure selected page is valid
  useEffect(() => {
    if (!currentPage || currentPage < 1 || (currentPage > totalPages && totalPages > 0)) {
      setCurrentPage(1);
      setSelectedPage(1);
    }
  }, [currentPage, totalPages, setSelectedPage]);
  
  // Sync with context selected page
  useEffect(() => {
    if (state.selectedPage && state.selectedPage !== currentPage) {
      setCurrentPage(state.selectedPage);
    }
  }, [state.selectedPage, currentPage]);
  
  // Cleanup on unmount
  useEffect(() => {
    return () => {
      isMounted.current = false;
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
    };
  }, [baseImageUrl, compareImageUrl]);
  
  // Fetch base page image - using direct fetch like SmartComparisonContainer
  const fetchBaseImage = useCallback(async (fileId, page) => {
    if (!fileId || page < 1) return null;
    
    console.log(`SideBySideView: Fetching base image for fileId=${fileId}, page=${page}`);
    
    try {
      // Use direct fetch instead of the API service
      const url = `/api/pdfs/document/${fileId}/page/${page}`;
      console.log(`SideBySideView: Fetch URL for base image: ${url}`);
      
      const response = await fetch(url);
      console.log(`SideBySideView: Base image fetch response status: ${response.status}`);
      
      if (!response.ok) {
        throw new Error(`Failed to fetch base image: ${response.status} ${response.statusText}`);
      }
      
      const blob = await response.blob();
      console.log(`SideBySideView: Base image blob received, size: ${blob.size} bytes, type: ${blob.type}`);
      
      if (!isMounted.current) return null;
      
      // Clean up previous URL
      if (baseImageUrl) {
        console.log(`SideBySideView: Revoking previous base image URL`);
        URL.revokeObjectURL(baseImageUrl);
      }
      
      const objectUrl = URL.createObjectURL(blob);
      console.log(`SideBySideView: Created base image object URL: ${objectUrl}`);
      return objectUrl;
    } catch (error) {
      console.error("Error fetching base image:", error);
      return null;
    }
  }, [baseImageUrl]);
  
  // Fetch compare page image - using direct fetch like SmartComparisonContainer
  const fetchCompareImage = useCallback(async (fileId, page) => {
    if (!fileId || page < 1) return null;
    
    console.log(`SideBySideView: Fetching compare image for fileId=${fileId}, page=${page}`);
    
    try {
      // Use direct fetch instead of the API service
      const url = `/api/pdfs/document/${fileId}/page/${page}`;
      console.log(`SideBySideView: Fetch URL for compare image: ${url}`);
      
      const response = await fetch(url);
      console.log(`SideBySideView: Compare image fetch response status: ${response.status}`);
      
      if (!response.ok) {
        throw new Error(`Failed to fetch compare image: ${response.status} ${response.statusText}`);
      }
      
      const blob = await response.blob();
      console.log(`SideBySideView: Compare image blob received, size: ${blob.size} bytes, type: ${blob.type}`);
      
      if (!isMounted.current) return null;
      
      // Clean up previous URL
      if (compareImageUrl) {
        console.log(`SideBySideView: Revoking previous compare image URL`);
        URL.revokeObjectURL(compareImageUrl);
      }
      
      const objectUrl = URL.createObjectURL(blob);
      console.log(`SideBySideView: Created compare image object URL: ${objectUrl}`);
      return objectUrl;
    } catch (error) {
      console.error("Error fetching compare image:", error);
      return null;
    }
  }, [compareImageUrl]);
  
  // Main data loading function
  const loadPageData = useCallback(async () => {
    if (!comparisonId || !currentPage || !baseFileId || !compareFileId) {
      console.error("SideBySideView: Missing required data for loading pages", {
        comparisonId,
        currentPage,
        baseFileId,
        compareFileId
      });
      return;
    }
    
    setIsLoading(true);
    console.log("Loading page data for SideBySideView...");
    
    try {
      // Calculate page numbers
      let basePage, comparePage;
      
      if (documentPair) {
        // Smart mode
        basePage = documentPair.baseStartPage + (currentPage - 1);
        comparePage = documentPair.compareStartPage + (currentPage - 1);
      } else {
        // Regular mode
        basePage = currentPage;
        comparePage = state.selectedPage;
      }
      
      // Ensure page numbers are valid
      basePage = Math.max(1, basePage);
      comparePage = Math.max(1, comparePage);
      
      console.log(`Fetching pages - Base: ${basePage}, Compare: ${comparePage}`);
      
      // Reset image URLs before loading new ones
      if (baseImageUrl) {
        URL.revokeObjectURL(baseImageUrl);
        setBaseImageUrl(null);
      }
      
      if (compareImageUrl) {
        URL.revokeObjectURL(compareImageUrl);
        setCompareImageUrl(null);
      }
      
      // DIRECT APPROACH: Load images directly without Promise.all
      // This approach is more similar to SmartComparisonContainer
      
      // First, try to get page details
      let details;
      try {
        details = await getDocumentPageDetails(comparisonId, selectedPairIndexProp, state.selectedPage, state.filters);
        console.log("SideBySideView: Got page details:", details);
      } catch (err) {
        console.error("Page details fetch error:", err);
        details = { baseDifferences: [], compareDifferences: [] };
      }
      
      if (details && isMounted.current) {
        setPageDetails(details);
      }
      
      // Then, try to get base image
      try {
        console.log(`SideBySideView: Fetching base image for page ${basePage}`);
        const baseUrl = await fetchBaseImage(state.baseFile.fileId, basePage);
        if (baseUrl && isMounted.current) {
          console.log("SideBySideView: Setting base image URL:", baseUrl);
          setBaseImageUrl(baseUrl);
        } else {
          console.warn("SideBySideView: No base image URL received");
        }
      } catch (err) {
        console.error("Base image fetch error:", err);
      }
      
      // Finally, try to get compare image
      try {
        console.log(`SideBySideView: Fetching compare image for page ${comparePage}`);
        const compareUrl = await fetchCompareImage(state.compareFile.fileId, comparePage);
        if (compareUrl && isMounted.current) {
          console.log("SideBySideView: Setting compare image URL:", compareUrl);
          setCompareImageUrl(compareUrl);
        } else {
          console.warn("SideBySideView: No compare image URL received");
        }
      } catch (err) {
        console.error("Compare image fetch error:", err);
      }
      
      if (isMounted.current) {
        setIsLoading(false);
      }
    } catch (error) {
      console.error("Error loading page data:", error);
      if (isMounted.current) {
        // Even if there's an error, try to continue with what we have
        setIsLoading(false);
      }
    }
  }, [
    comparisonId,
    state.selectedPage,
    state.filters,
    state.baseFile,
    state.compareFile,
    selectedPairIndexProp,
    documentPair,
    fetchBaseImage,
    fetchCompareImage
  ]);
  
  // Load data when dependencies change
  useEffect(() => {
    loadPageData();
  }, [loadPageData]);
  
  // Handle difference selection
  const handleDifferenceSelect = useCallback((diff) => {
    if (!diff) return;
    setSelectedDifference(diff);
  }, [setSelectedDifference]);
  
  // Toggle difference panel
  const toggleDifferencePanel = useCallback(() => {
    setShowDifferencePanel(prev => !prev);
  }, []);
  
  // Page navigation
  const goToPage = useCallback((pageNum) => {
    if (pageNum >= 1 && pageNum <= totalPages) {
      setSelectedPage(pageNum);
    }
  }, [totalPages, setSelectedPage]);
  
  // If no result yet, show loading
  if (!result) {
    return (
      <div className="side-by-side-view-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }
  
  return (
    <div className="side-by-side-view">
      <div className="page-navigation">
        <button 
          className="nav-button prev"
          onClick={() => goToPage(state.selectedPage - 1)}
          disabled={state.selectedPage <= 1}
        >
          <svg viewBox="0 0 24 24">
            <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
          </svg>
          <span>Previous</span>
        </button>
        
        <div className="page-indicator">
          Page 
          <input 
            type="number"
            value={state.selectedPage || 1}
            min={1}
            max={totalPages}
            onChange={(e) => {
              const page = parseInt(e.target.value);
              if (!isNaN(page) && page >= 1 && page <= totalPages) {
                goToPage(page);
              }
            }}
          /> 
          of {totalPages}
        </div>
        
        <button 
          className="nav-button next"
          onClick={() => goToPage(state.selectedPage + 1)}
          disabled={state.selectedPage >= totalPages}
        >
          <span>Next</span>
          <svg viewBox="0 0 24 24">
            <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
          </svg>
        </button>
      </div>
      
      <div className="comparison-container">
        <div className={`pdf-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
          <div className="document-header-bar">
            <div className="document-header base">Base Document</div>
            <div className="document-header compare">Compare Document</div>
          </div>
          
          <div className="document-content">
            <div className="pdf-view base">
              <PDFPage 
                imageUrl={baseImageUrl}
                differences={pageDetails?.baseDifferences || []}
                onSelectDifference={handleDifferenceSelect}
                selectedDifference={state.selectedDifference}
              />
            </div>
            
            <div className="pdf-view compare">
              <PDFPage 
                imageUrl={compareImageUrl}
                differences={pageDetails?.compareDifferences || []}
                onSelectDifference={handleDifferenceSelect}
                selectedDifference={state.selectedDifference}
              />
            </div>
          </div>
        </div>
        
        {showDifferencePanel && (
          <div className="difference-panel">
            <EnhancedDifferenceList
              differences={pageDetails || { baseDifferences: [], compareDifferences: [] }}
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
          {showDifferencePanel ? '›' : '‹'}
        </button>
      </div>
      
      {isLoading && (
        <div className="loading-overlay">
          <Spinner size="medium" />
        </div>
      )}
    </div>
  );
};

export default SideBySideView;
