import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import SimplifiedDifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import { getDocumentPairs, getDocumentPageDetails } from '../../services/api';
import './SideBySideView.css';

const SideBySideView = ({ comparisonId }) => {
  const isMounted = useRef(true);
  const fetchInProgress = useRef(false);
  
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
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDetails, setPageDetails] = useState(null);
  const [baseDifferences, setBaseDifferences] = useState([]);
  const [compareDifferences, setCompareDifferences] = useState([]);
  const [baseImageUrl, setBaseImageUrl] = useState(null);
  const [compareImageUrl, setCompareImageUrl] = useState(null);
  const [loadingImages, setLoadingImages] = useState(false);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);

  // Safely extract selected pair from state
  const selectedPair = useMemo(() => {
    // Prioritize the selectedPair in state
    if (state.documentPairs && state.documentPairs.length > 0) {
      return state.documentPairs[state.selectedDocumentPairIndex || 0];
    }
    return null;
  }, [state.documentPairs, state.selectedDocumentPairIndex]);

  // Memoized max page count calculation
  const maxPageCount = useMemo(() => {
    if (!selectedPair) return 1;
    const basePages = selectedPair.baseEndPage - selectedPair.baseStartPage + 1;
    const comparePages = selectedPair.compareEndPage - selectedPair.compareStartPage + 1;
    return Math.max(basePages, comparePages);
  }, [selectedPair]);

  // Fetch document pairs on mount
  useEffect(() => {
    const fetchDocumentPairs = async () => {
      if (!comparisonId || fetchInProgress.current) return;
      
      try {
        fetchInProgress.current = true;
        setLoading(true);
        
        const pairs = await getDocumentPairs(comparisonId);
        
        if (pairs && pairs.length > 0) {
          // Set document pairs in context
          setDocumentPairs(pairs);
          
          // Find first matched pair
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;
          
          // Set selected pair index
          setSelectedDocumentPairIndex(initialIndex);
          
          // Switch to comparison view
          setActiveView('comparison');
        } else {
          throw new Error("No document pairs returned");
        }
      } catch (err) {
        console.error("Error fetching document pairs:", err);
        setError("Failed to load document pairs: " + err.message);
      } finally {
        setLoading(false);
        fetchInProgress.current = false;
      }
    };
    
    fetchDocumentPairs();
  }, [comparisonId]);

  // Fetch page details and images
  useEffect(() => {
    const fetchPageData = async () => {
      // Ensure we have all required data
      if (!comparisonId || !selectedPair || 
          !state.baseFile?.fileId || 
          !state.compareFile?.fileId) {
        return;
      }

      setLoadingImages(true);
      setPageDetails(null);
      setBaseDifferences([]);
      setCompareDifferences([]);

      // Clean up previous blob URLs
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
      setBaseImageUrl(null);
      setCompareImageUrl(null);

      try {
        // Fetch page details
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          currentPage,
          state.filters
        );

        // Process differences
        if (details) {
          setPageDetails(details);
          setBaseDifferences(details.baseDifferences || []);
          setCompareDifferences(details.compareDifferences || []);
        }

        // Calculate specific page numbers
        const basePage = selectedPair.baseStartPage + currentPage - 1;
        const comparePage = selectedPair.compareStartPage + currentPage - 1;

        // Fetch images
        const fetchImage = async (fileId, pageNum, docType) => {
          if (!fileId || pageNum < 1) return null;

          try {
            const response = await fetch(`/api/pdfs/document/${fileId}/page/${pageNum}`);
            if (!response.ok) {
              throw new Error(`Failed to fetch ${docType} page ${pageNum}`);
            }
            const blob = await response.blob();
            return URL.createObjectURL(blob);
          } catch (error) {
            console.error(`Error fetching ${docType} image:`, error);
            return null;
          }
        };

        // Fetch base and compare images
        const [baseUrl, compareUrl] = await Promise.all([
          fetchImage(state.baseFile.fileId, basePage, 'base'),
          fetchImage(state.compareFile.fileId, comparePage, 'compare')
        ]);

        setBaseImageUrl(baseUrl);
        setCompareImageUrl(compareUrl);

      } catch (error) {
        console.error("Error loading page data:", error);
        setError(`Failed to load page data: ${error.message}`);
      } finally {
        setLoadingImages(false);
      }
    };

    fetchPageData();
  }, [
    comparisonId, 
    selectedPair, 
    currentPage, 
    state.selectedDocumentPairIndex, 
    state.baseFile?.fileId,
    state.compareFile?.fileId,
    state.filters
  ]);

  // Render for document matching view
  if (activeView === 'matching') {
    return (
      <DocumentMatchingView 
        comparisonId={comparisonId}
        onSelectDocumentPair={(pairIndex, pair) => {
          setSelectedDocumentPairIndex(pairIndex);
          setActiveView('comparison');
        }}
      />
    );
  }

  // If no selected pair, show loading
  if (!selectedPair) {
    return (
      <div className="loading-container">
        <Spinner size="large" />
        <p>Loading document comparison...</p>
      </div>
    );
  }

  // Render comparison view
  return (
    <div className="pdf-comparison-container">
      <div className="comparison-content">
        <div className={`documents-container ${showDifferencePanel ? 'with-panel' : ''}`}>
          <div className="document-view base-document">
            <div className="document-header">
              <h4>Base Document</h4>
            </div>
            <div className="document-content">
              {loadingImages ? (
                <Spinner size="medium" />
              ) : baseImageUrl ? (
                <PDFRenderer 
                  fileId={state.baseFile?.fileId}
                  page={selectedPair.baseStartPage + currentPage - 1}
                  differences={baseDifferences}
                  highlightMode={highlightMode}
                />
              ) : (
                <div>No base document page available</div>
              )}
            </div>
          </div>
          
          <div className="document-view compare-document">
            <div className="document-header">
              <h4>Compare Document</h4>
            </div>
            <div className="document-content">
              {loadingImages ? (
                <Spinner size="medium" />
              ) : compareImageUrl ? (
                <PDFRenderer 
                  fileId={state.compareFile?.fileId}
                  page={selectedPair.compareStartPage + currentPage - 1}
                  differences={compareDifferences}
                  highlightMode={highlightMode}
                />
              ) : (
                <div>No compare document page available</div>
              )}
            </div>
          </div>
        </div>
        
        {showDifferencePanel && (
          <div className="difference-panel">
            <SimplifiedDifferenceList 
              result={pageDetails}
            />
          </div>
        )}
      </div>
      
      <div className="comparison-footer">
        <div className="page-navigation">
          <button 
            onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
            disabled={currentPage <= 1 || loadingImages}
          >
            Previous Page
          </button>
          
          <div className="page-indicator">
            Page {currentPage} of {maxPageCount}
          </div>
          
          <button 
            onClick={() => setCurrentPage(prev => Math.min(maxPageCount, prev + 1))}
            disabled={currentPage >= maxPageCount || loadingImages}
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
      </div>
    </div>
  );
};

export default SideBySideView;