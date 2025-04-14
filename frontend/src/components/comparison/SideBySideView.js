import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import DifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import ZoomControls from './ZoomControls';
import { getDocumentPairs, getDocumentPageDetails } from '../../services/api';
import './SideBySideView.css';

const SideBySideView = ({ comparisonId }) => {
  // Refs
  const containerRef = useRef(null);
  const scrollContainerRef = useRef(null);
  const fetchingRef = useRef(false);
  const didInitialFetchRef = useRef(false);
  const visibleDifferencesRef = useRef([]);

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
  const [pairDifferences, setPairDifferences] = useState({});
  const [currentVisibleDifferences, setCurrentVisibleDifferences] = useState([]);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [zoom, setZoom] = useState(0.750);
  const [pageMetadata, setPageMetadata] = useState({});
  const [visiblePairIndices, setVisiblePairIndices] = useState([]);
  const [isScrolling, setIsScrolling] = useState(false);
  const [loadingPairs, setLoadingPairs] = useState([]);
  
  // Basic coordinate adjustment parameters
  const adjustmentParams = {
    xOffset: 0,
    yOffset: 0,
    scaleAdjustment: 1.0,
    flipY: true
  };
  
  // Get all document pairs from context
  const documentPairs = state.documentPairs || [];

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
          
          // Auto-select the first matched pair
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;
          
          setSelectedDocumentPairIndex(initialIndex);
          
          if (matchedPairIndex >= 0) {
            setActiveView('comparison');
            
            // Initialize with all matched pairs visible
            const matched = pairs.filter(pair => pair.matched);
            setVisiblePairIndices(matched.map((_, index) => index));
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
  }, [comparisonId]); // Only depend on comparisonId

  // Effect to fetch differences for visible pairs
  useEffect(() => {
    const fetchVisiblePairsDifferences = async () => {
      // Skip if no visible pairs or already fetching
      if (visiblePairIndices.length === 0 || fetchingRef.current || activeView !== 'comparison') {
        return;
      }
      
      fetchingRef.current = true;
      
      try {
        // Keep track of loading pairs
        setLoadingPairs(visiblePairIndices);
        
        // For each visible pair, fetch differences if not already loaded
        for (const pairIndex of visiblePairIndices) {
          if (!pairDifferences[pairIndex] && documentPairs[pairIndex]?.matched) {
            console.log(`Fetching differences for pair ${pairIndex}...`);
            
            try {
              const details = await getDocumentPageDetails(
                comparisonId,
                pairIndex,
                1, // Start with page 1
                state.filters
              );
              
              if (details) {
                // Save differences for this pair
                setPairDifferences(prev => ({
                  ...prev,
                  [pairIndex]: {
                    baseDifferences: details.baseDifferences || [],
                    compareDifferences: details.compareDifferences || [],
                    baseWidth: details.baseWidth,
                    baseHeight: details.baseHeight,
                    compareWidth: details.compareWidth,
                    compareHeight: details.compareHeight
                  }
                }));
                
                // Update page metadata
                setPageMetadata(prev => ({
                  ...prev,
                  [pairIndex]: {
                    baseWidth: details.baseWidth,
                    baseHeight: details.baseHeight,
                    compareWidth: details.compareWidth,
                    compareHeight: details.compareHeight
                  }
                }));
              }
            } catch (err) {
              console.error(`Error fetching differences for pair ${pairIndex}:`, err);
            }
          }
        }
        
        // Update visible differences
        updateVisibleDifferences();
      } finally {
        fetchingRef.current = false;
        setLoadingPairs([]);
      }
    };
    
    fetchVisiblePairsDifferences();
  }, [visiblePairIndices, activeView, comparisonId]);

  // Update visible differences when pairDifferences changes
  useEffect(() => {
    updateVisibleDifferences();
  }, [pairDifferences]);

  // Function to update visible differences
  const updateVisibleDifferences = useCallback(() => {
    // Collect all differences from visible pairs
    let allVisibleDiffs = [];
    
    visiblePairIndices.forEach(pairIndex => {
      const pairData = pairDifferences[pairIndex];
      if (!pairData) return;
      
      // Add pair index to each difference
      const baseDiffsWithInfo = (pairData.baseDifferences || []).map(diff => ({
        ...diff,
        pairIndex
      }));
      
      const compareDiffsWithInfo = (pairData.compareDifferences || []).map(diff => ({
        ...diff,
        pairIndex
      }));
      
      allVisibleDiffs = [...allVisibleDiffs, ...baseDiffsWithInfo, ...compareDiffsWithInfo];
    });
    
    // Deduplicate by difference ID
    const uniqueDiffs = [];
    const diffIds = new Set();
    
    allVisibleDiffs.forEach(diff => {
      if (!diffIds.has(diff.id)) {
        diffIds.add(diff.id);
        uniqueDiffs.push(diff);
      }
    });
    
    // Sort by pair index
    uniqueDiffs.sort((a, b) => {
      if (a.pairIndex !== b.pairIndex) {
        return a.pairIndex - b.pairIndex;
      }
      return 0;
    });
    
    console.log(`Found ${uniqueDiffs.length} visible differences`);
    
    // Update state
    setCurrentVisibleDifferences(uniqueDiffs);
  }, [visiblePairIndices, pairDifferences]);

  // Handle scroll events to determine visible pairs
  const handleScroll = useCallback(() => {
    if (!scrollContainerRef.current || isScrolling || documentPairs.length === 0) return;
    
    // Set scrolling state to avoid too many updates
    setIsScrolling(true);
    
    // Get all pair elements
    const pairElements = document.querySelectorAll('.document-pair');
    if (!pairElements.length) {
      setIsScrolling(false);
      return;
    }
    
    const scrollContainer = scrollContainerRef.current;
    const containerRect = scrollContainer.getBoundingClientRect();
    
    // Determine which pairs are visible
    const visible = [];
    
    pairElements.forEach((element, index) => {
      const rect = element.getBoundingClientRect();
      
      // Check if element is at least partially visible
      if (rect.bottom >= containerRect.top && rect.top <= containerRect.bottom) {
        visible.push(index);
      }
    });
    
    // Update visible pairs if changed
    if (JSON.stringify(visible) !== JSON.stringify(visiblePairIndices)) {
      setVisiblePairIndices(visible);
    }
    
    // Reset scrolling state after a short delay
    setTimeout(() => {
      setIsScrolling(false);
    }, 100);
  }, [isScrolling, documentPairs, visiblePairIndices]);

  // Set up scroll event listener
  useEffect(() => {
    const scrollContainer = scrollContainerRef.current;
    
    if (scrollContainer && activeView === 'comparison') {
      scrollContainer.addEventListener('scroll', handleScroll);
      
      // Initial scroll calculation
      setTimeout(handleScroll, 100);
      
      return () => {
        scrollContainer.removeEventListener('scroll', handleScroll);
      };
    }
  }, [handleScroll, activeView]);

  // Handle document pair selection
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("Selecting document pair:", pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    setActiveView('comparison');
    
    // Scroll to this pair
    setTimeout(() => {
      const pairElement = document.getElementById(`document-pair-${pairIndex}`);
      if (pairElement && scrollContainerRef.current) {
        pairElement.scrollIntoView({ behavior: 'smooth' });
      }
    }, 100);
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

  // Show loading state if no document pairs
  if (documentPairs.length === 0) {
    return (
      <div className="loading-container">
        <Spinner size="large" />
        <p>Loading document comparisons...</p>
      </div>
    );
  }

  // Helper function to get color based on similarity score
  const getSimilarityColor = (score) => {
    if (score >= 0.95) return '#4CAF50'; // Green
    if (score >= 0.85) return '#8BC34A'; // Light Green
    if (score >= 0.70) return '#CDDC39'; // Lime
    if (score >= 0.50) return '#FFC107'; // Amber
    if (score > 0) return '#FF9800';     // Orange
    return '#F44336';                    // Red
  };
  
  return (
    <div className="pdf-comparison-container" ref={containerRef}>
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
          <div className="page-ranges">
            <span>{documentPairs.filter(p => p.matched).length} document pairs matched</span>
          </div>
        </div>
        
        <ZoomControls
          zoom={zoom}
          onZoomIn={() => handleZoomChange(Math.min(2.0, zoom + 0.25))}
          onZoomOut={() => handleZoomChange(Math.max(0.5, zoom - 0.25))}
          onZoomReset={() => handleZoomChange(1.0)}
        />
      </div>
      
      {/* Main content with continuous scroll */}
      <div className="comparison-content">
        {/* Main scrollable area */}
        <div 
          className={`documents-scroll-container ${showDifferencePanel ? 'with-panel' : ''}`}
          ref={scrollContainerRef}
        >
          <div className="continuous-pdf-view">
            {/* Render all matched document pairs */}
            {documentPairs.map((pair, pairIndex) => {
              if (!pair.matched) return null;
              
              // Get differences for this pair
              const pairData = pairDifferences[pairIndex] || {
                baseDifferences: [],
                compareDifferences: []
              };
              
              // Get metadata for this pair
              const pairMeta = pageMetadata[pairIndex];
              
              // Calculate page ranges
              const basePageRange = {
                start: pair.baseStartPage,
                end: pair.baseEndPage
              };
              
              const comparePageRange = {
                start: pair.compareStartPage,
                end: pair.compareEndPage
              };
              
              const isLoading = loadingPairs.includes(pairIndex);
              
              return (
                <div 
                  className="document-pair" 
                  key={`pair-${pairIndex}`} 
                  id={`document-pair-${pairIndex}`}
                >
                  <div className="pair-header">
                    <h3>Document {pairIndex + 1}</h3>
                    {pair.similarityScore !== undefined && (
                      <div className="match-percentage" style={{
                        background: getSimilarityColor(pair.similarityScore),
                        color: 'white'
                      }}>
                        {Math.round(pair.similarityScore * 100)}% Match
                      </div>
                    )}
                  </div>
                  
                  <div className="page-content">
                    <div className="page-side base-side">
                      <div className="document-header">
                        <h4>Base Document</h4>
                        <div className="page-info">
                          Pages {basePageRange.start} - {basePageRange.end}
                        </div>
                      </div>
                      
                      <div className="document-content">
                        {state.baseFile?.fileId ? (
                          <PDFRenderer
                            fileId={state.baseFile.fileId}
                            page={basePageRange.start}
                            zoom={zoom}
                            highlightMode={highlightMode}
                            differences={pairData.baseDifferences || []}
                            selectedDifference={state.selectedDifference}
                            onDifferenceSelect={(diff) => setSelectedDifference({...diff, pairIndex})}
                            onZoomChange={handleZoomChange}
                            isBaseDocument={true}
                            loading={isLoading}
                            pageMetadata={pairMeta}
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
                    
                    <div className="page-side compare-side">
                      <div className="document-header">
                        <h4>Compare Document</h4>
                        <div className="page-info">
                          Pages {comparePageRange.start} - {comparePageRange.end}
                        </div>
                      </div>
                      
                      <div className="document-content">
                        {state.compareFile?.fileId ? (
                          <PDFRenderer
                            fileId={state.compareFile.fileId}
                            page={comparePageRange.start}
                            zoom={zoom}
                            highlightMode={highlightMode}
                            differences={pairData.compareDifferences || []}
                            selectedDifference={state.selectedDifference}
                            onDifferenceSelect={(diff) => setSelectedDifference({...diff, pairIndex})}
                            onZoomChange={handleZoomChange}
                            isBaseDocument={false}
                            loading={isLoading}
                            pageMetadata={pairMeta}
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
              );
            })}
          </div>
        </div>
        
        {/* Difference panel */}
        {showDifferencePanel && (
          <div className="difference-panel">
            <div className="difference-panel-header">
              <h3>Visible Differences</h3>
              <div className="difference-count">
                {currentVisibleDifferences.length} differences in view
              </div>
            </div>
            
            <DifferenceList
              result={{ 
                baseDifferences: currentVisibleDifferences, 
                compareDifferences: [] // We're already combining them
              }}
              selectedDifference={state.selectedDifference}
              onDifferenceClick={(diff) => {
                setSelectedDifference(diff);
                
                // Scroll to the pair containing this difference
                if (diff.pairIndex !== undefined) {
                  const pairElement = document.getElementById(`document-pair-${diff.pairIndex}`);
                  if (pairElement) {
                    pairElement.scrollIntoView({ behavior: 'smooth' });
                  }
                }
              }}
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
        <div className="view-controls">
          <div className="highlight-controls">
            <label htmlFor="highlightSelect">Highlight: </label>
            <select 
              id="highlightSelect"
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
          
          <div className="page-status">
            Viewing {visiblePairIndices.length} of {documentPairs.filter(p => p.matched).length} document pairs
          </div>
        </div>
      </div>
    </div>
  );
};

export default SideBySideView;