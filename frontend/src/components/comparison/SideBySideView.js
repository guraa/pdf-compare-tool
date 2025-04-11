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
  const scrollContainerRef = useRef(null);
  const baseContentRef = useRef(null);
  const compareContentRef = useRef(null);

  const {
    state,
    setError,
    setLoading,
    setDocumentPairs,
    setSelectedDocumentPairIndex,
    setSelectedPage,
    setSelectedDifference,
    updateViewSettings
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
  const [zoom, setZoom] = useState(1.0);

  useEffect(() => {
    return () => {
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
    };
  }, [baseImageUrl, compareImageUrl]);

  const selectedPair = useMemo(() => {
    if (state.documentPairs && state.documentPairs.length > 0) {
      return state.documentPairs[state.selectedDocumentPairIndex || 0];
    }
    return null;
  }, [state.documentPairs, state.selectedDocumentPairIndex]);

  const maxPageCount = useMemo(() => {
    if (!selectedPair) return 1;
    const basePages = selectedPair.baseEndPage - selectedPair.baseStartPage + 1;
    const comparePages = selectedPair.compareEndPage - selectedPair.compareStartPage + 1;
    return Math.max(basePages, comparePages);
  }, [selectedPair]);

  useEffect(() => {
    const fetchDocumentPairs = async () => {
      if (!comparisonId || fetchInProgress.current) return;

      try {
        fetchInProgress.current = true;
        setLoading(true);

        const pairs = await getDocumentPairs(comparisonId);

        if (pairs && pairs.length > 0) {
          setDocumentPairs(pairs);
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;

          setSelectedDocumentPairIndex(initialIndex);

          if (matchedPairIndex >= 0) {
            setActiveView('comparison');
            setCurrentPage(1);
          }
        } else {
          throw new Error("No document pairs returned");
        }
      } catch (err) {
        console.error("Error fetching document pairs:", err);
        setError("Failed to load document pairs: " + err.message);
      } finally {
        if (isMounted.current) {
          setLoading(false);
          fetchInProgress.current = false;
        }
      }
    };

    fetchDocumentPairs();
  }, [comparisonId]);

  useEffect(() => {
    const fetchPageData = async () => {
      if (!comparisonId || !selectedPair ||
        !state.baseFile?.fileId ||
        !state.compareFile?.fileId ||
        activeView !== 'comparison') {
        return;
      }

      setLoadingImages(true);
      setPageDetails(null);
      setBaseDifferences([]);
      setCompareDifferences([]);

      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
      setBaseImageUrl(null);
      setCompareImageUrl(null);

      try {
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          currentPage,
          state.filters
        );

        if (details) {
          setPageDetails(details);
          setBaseDifferences(details.baseDifferences || []);
          setCompareDifferences(details.compareDifferences || []);
        }

        const basePage = selectedPair.baseStartPage + currentPage - 1;
        const comparePage = selectedPair.compareStartPage + currentPage - 1;

        const fetchImage = async (fileId, pageNum, docType) => {
          if (!fileId || pageNum < 1) return null;

          try {
            const response = await fetch(`/api/pdfs/document/${fileId}/page/${pageNum}`);
            if (!response.ok) {
              throw new Error(`Failed to fetch ${docType} page ${pageNum}: ${response.statusText}`);
            }
            const blob = await response.blob();
            return URL.createObjectURL(blob);
          } catch (error) {
            console.error(`Error fetching ${docType} image:`, error);
            return null;
          }
        };

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
    state.filters,
    activeView
  ]);

  // Zoom handlers
  const handleZoomIn = () => {
    const newZoom = Math.min(zoom + 0.25, 3.0);
    setZoom(newZoom);
    updateViewSettings({ zoom: newZoom });
  };

  const handleZoomOut = () => {
    const newZoom = Math.max(zoom - 0.25, 0.5);
    setZoom(newZoom);
    updateViewSettings({ zoom: newZoom });
  };

  // Sync scrolling
  useEffect(() => {
    const container = scrollContainerRef.current;
    const syncScroll = (e) => {
      if (baseContentRef.current && compareContentRef.current) {
        const scrollTop = e.target.scrollTop;
        baseContentRef.current.scrollTop = scrollTop;
        compareContentRef.current.scrollTop = scrollTop;
      }
    };

    if (container) {
      container.addEventListener('scroll', syncScroll);
    }

    return () => {
      if (container) {
        container.removeEventListener('scroll', syncScroll);
      }
    };
  }, []);

  if (activeView === 'matching') {
    return (
      <DocumentMatchingView
        comparisonId={comparisonId}
        onSelectDocumentPair={(pairIndex, pair) => {
          setSelectedDocumentPairIndex(pairIndex);
          setCurrentPage(1);
          setActiveView('comparison');
        }}
      />
    );
  }

  if (!selectedPair) {
    return (
      <div className="loading-container">
        <Spinner size="large" />
        <p>Loading document comparison...</p>
      </div>
    );
  }

  return (
    <div className="pdf-comparison-container">
      <div className="comparison-header">
        <div className="zoom-controls">
          <button onClick={handleZoomOut} title="Zoom Out">-</button>
          <button onClick={() => setZoom(1.0)} title="Reset Zoom">
            {Math.round(zoom * 100)}%
          </button>
          <button onClick={handleZoomIn} title="Zoom In">+</button>
        </div>
      </div>

      <div className="comparison-content">
        <div
          className={`documents-container-scroll-wrapper ${showDifferencePanel ? 'with-panel' : ''}`}
          ref={scrollContainerRef}
        >
          <div className="documents-inner">
            <div className="document-view base-document" ref={baseContentRef}>
              <div className="document-header">
                <h4>Base Document</h4>
                <div className="page-info">
                  Page {selectedPair.baseStartPage + currentPage - 1}
                </div>
              </div>
              <div className="document-content">
                {loadingImages ? (
                  <Spinner size="medium" />
                ) : baseImageUrl ? (
                  <img
                    src={baseImageUrl}
                    alt={`Base document page ${selectedPair.baseStartPage + currentPage - 1}`}
                    style={{
                      width: `${100 * zoom}%`,
                      height: 'auto',
                      objectFit: 'contain'
                    }}
                  />
                ) : (
                  <div>No base document page available</div>
                )}
              </div>
            </div>

            <div className="document-view compare-document" ref={compareContentRef}>
              <div className="document-header">
                <h4>Compare Document</h4>
                <div className="page-info">
                  Page {selectedPair.compareStartPage + currentPage - 1}
                </div>
              </div>
              <div className="document-content">
                {loadingImages ? (
                  <Spinner size="medium" />
                ) : compareImageUrl ? (
                  <img
                    src={compareImageUrl}
                    alt={`Compare document page ${selectedPair.compareStartPage + currentPage - 1}`}
                    style={{
                      width: `${100 * zoom}%`,
                      height: 'auto',
                      objectFit: 'contain'
                    }}
                  />
                ) : (
                  <div>No compare document page available</div>
                )}
              </div>
            </div>
          </div>
        </div>

        {showDifferencePanel && (
          <div className="difference-panel">
            <SimplifiedDifferenceList result={pageDetails} />
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

          <div className="panel-toggle">
            <button onClick={() => setShowDifferencePanel(prev => !prev)}>
              {showDifferencePanel ? 'Hide Differences' : 'Show Differences'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SideBySideView;
