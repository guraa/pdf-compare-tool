import React, { useState, useEffect, useRef, memo } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import DifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import ZoomControls from './ZoomControls';
import { getDocumentPairs, getComparisonResult } from '../../services/api';
import './SideBySideView.css';

// Create a memoized DifferenceList to prevent unnecessary rerenders
const MemoizedDifferenceList = memo(DifferenceList);

// Simplified, highly optimized SideBySideView
const SideBySideView = React.memo(({ comparisonId }) => {
  console.log("SideBySideView initial render with comparisonId:", comparisonId);

  // Refs to prevent dependency issues
  const initialFetchDoneRef = useRef(false);
  const comparisonDataRef = useRef(null);
  const scrollContainerRef = useRef(null);
  const visiblePairIndicesRef = useRef([]);

  // Context
  const {
    state,
    setError,
    setLoading,
    setDocumentPairs,
    setSelectedDocumentPairIndex,
    setSelectedDifference,
    setComparisonResult
  } = useComparison();

  // State
  const [activeView, setActiveView] = useState('matching');
  const [pairDifferences, setPairDifferences] = useState({});
  const [currentVisibleDifferences, setCurrentVisibleDifferences] = useState([]);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [zoom, setZoom] = useState(0.750);
  const [loadingPairs, setLoadingPairs] = useState([]);
  const [viewKey, setViewKey] = useState(0); // Used to force re-render when needed
  
  // Calibration parameters
  const adjustmentParams = {
    xOffset: 0,
    yOffset: 0,
    scaleAdjustment: 1.0,
    flipY: true
  };

  // One-time fetch for comparison data
  useEffect(() => {
    if (!comparisonId || initialFetchDoneRef.current) return;

    const fetchData = async () => {
      try {
        initialFetchDoneRef.current = true;
        setLoading(true);

        console.log("Fetching comparison data...");
        const result = await getComparisonResult(comparisonId);
        
        if (result) {
          console.log("Comparison data received:", result);
          comparisonDataRef.current = result;
          setComparisonResult(result);

          // Process differences from the data
          if (result.differencesByPage) {
            const processedDifferences = processComparisonData(result);
            setPairDifferences(processedDifferences);
          }

          // Also fetch document pairs to get proper metadata
          const pairs = await getDocumentPairs(comparisonId);
          
          if (pairs && pairs.length > 0) {
            console.log("Document pairs received:", pairs);
            setDocumentPairs(pairs);
            
            // Auto-select the first matched pair
            const matchedPairIndex = pairs.findIndex(pair => pair.matched);
            const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;
            
            setSelectedDocumentPairIndex(initialIndex);
            
            if (matchedPairIndex >= 0) {
              setActiveView('comparison');
              visiblePairIndicesRef.current = [matchedPairIndex];
              updateVisibleDifferences();
            }
          }
        }
      } catch (err) {
        console.error("Error fetching comparison data:", err);
        setError("Failed to load comparison: " + err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [comparisonId]); // Only dependency is comparisonId

  // Process the comparison data into the format needed for rendering
  const processComparisonData = (data) => {
    if (!data || !data.pagePairs || !data.differencesByPage) {
      return {};
    }

    const processedDifferences = {};
    
    // Process each page pair
    data.pagePairs.forEach((pair, index) => {
      const pageKey = pair.id;
      const differences = data.differencesByPage[pageKey] || [];
      
      if (differences.length > 0) {
        // Split differences for base and compare documents
        const baseDifferences = differences.map(diff => ({
          ...diff,
          id: `base-${diff.id || Math.random().toString(36).substr(2, 11)}`,
          page: diff.basePageNumber || 1
        }));
        
        const compareDifferences = differences.map(diff => ({
          ...diff,
          id: `compare-${diff.id || Math.random().toString(36).substr(2, 11)}`,
          page: diff.comparePageNumber || 1
        }));
        
        // Store differences for this pair
        processedDifferences[index] = {
          baseDifferences,
          compareDifferences,
          // Use standard page dimensions
          baseWidth: 612,
          baseHeight: 792,
          compareWidth: 612,
          compareHeight: 792
        };
      }
    });
    
    return processedDifferences;
  };
  
  // Update visible differences based on visible pairs
  const updateVisibleDifferences = () => {
    if (!pairDifferences || Object.keys(pairDifferences).length === 0) return;
    
    // Get visible pairs
    const visibleIndices = visiblePairIndicesRef.current;
    if (visibleIndices.length === 0) return;
    
    // Collect differences from visible pairs
    let allDiffs = [];
    
    visibleIndices.forEach(pairIndex => {
      const pairData = pairDifferences[pairIndex];
      if (!pairData) return;
      
      const baseDiffs = (pairData.baseDifferences || []).map(diff => ({
        ...diff,
        pairIndex
      }));
      
      const compareDiffs = (pairData.compareDifferences || []).map(diff => ({
        ...diff,
        pairIndex
      }));
      
      allDiffs = [...allDiffs, ...baseDiffs, ...compareDiffs];
    });
    
    // Deduplicate
    const uniqueDiffs = [];
    const diffIds = new Set();
    
    allDiffs.forEach(diff => {
      if (!diffIds.has(diff.id)) {
        diffIds.add(diff.id);
        uniqueDiffs.push(diff);
      }
    });
    
    console.log(`Found ${uniqueDiffs.length} visible differences`);
    setCurrentVisibleDifferences(uniqueDiffs);
  };

  // Handle scroll events
  const handleScroll = () => {
    if (!scrollContainerRef.current) return;
    
    // Find visible document pairs
    const pairElements = document.querySelectorAll('.document-pair');
    if (!pairElements.length) return;
    
    const containerRect = scrollContainerRef.current.getBoundingClientRect();
    const visiblePairs = [];
    
    pairElements.forEach((element, index) => {
      const rect = element.getBoundingClientRect();
      
      // Check if element is visible
      if (rect.bottom >= containerRect.top && rect.top <= containerRect.bottom) {
        visiblePairs.push(index);
      }
    });
    
    // Only update if changed
    if (JSON.stringify(visiblePairs) !== JSON.stringify(visiblePairIndicesRef.current)) {
      visiblePairIndicesRef.current = visiblePairs;
      updateVisibleDifferences();
    }
  };

  // Set up scroll handler
  useEffect(() => {
    const scrollContainer = scrollContainerRef.current;
    if (!scrollContainer || activeView !== 'comparison') return;
    
    const throttledScrollHandler = () => {
      if (!window.requestAnimationFrame) {
        setTimeout(handleScroll, 100);
        return;
      }
      
      window.requestAnimationFrame(handleScroll);
    };
    
    scrollContainer.addEventListener('scroll', throttledScrollHandler);
    setTimeout(handleScroll, 100);
    
    return () => {
      scrollContainer.removeEventListener('scroll', throttledScrollHandler);
    };
  }, [activeView]);

  // Switch to document pair
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("Selecting document pair:", pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    setActiveView('comparison');
    
    // Force rerender to ensure UI updates properly
    setViewKey(prevKey => prevKey + 1);
    
    // Update visible pairs
    visiblePairIndicesRef.current = [pairIndex];
    updateVisibleDifferences();
    
    // Scroll to the pair
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

  // Get all document pairs from context
  const documentPairs = state.documentPairs || [];

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
    <div className="pdf-comparison-container" key={viewKey}>
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
      
      {/* Main content */}
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
                            pageMetadata={pairData}
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
                            pageMetadata={pairData}
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
            
            <MemoizedDifferenceList
              result={{ 
                baseDifferences: currentVisibleDifferences, 
                compareDifferences: [] // Already combined
              }}
              selectedDifference={state.selectedDifference}
              onDifferenceClick={(diff) => {
                console.log("Selected difference:", diff);
                setSelectedDifference(diff);
                
                // Scroll to the pair
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
            Viewing {visiblePairIndicesRef.current.length} of {documentPairs.filter(p => p.matched).length} document pairs
          </div>
        </div>
      </div>
    </div>
  );
});

export default SideBySideView;