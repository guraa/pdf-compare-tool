import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import SideBySideView from './SideBySideView'; // Import the SideBySideView component
import Spinner from '../common/Spinner';
import { getDocumentPairs } from '../../services/api';
import './SmartComparisonContainer.css';

/**
 * Radical Bypass Solution
 * This version completely bypasses both data loading AND the problematic SideBySideView component
 * by implementing a bare-minimum PDF viewer directly
 */
const SmartComparisonContainer = ({ comparisonId }) => {
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
  const [selectedPairIndex, setSelectedPairIndex] = useState(0); // Default to 0
  const [comparisonResultData, setComparisonResultData] = useState(null); // Store full result
  // Removed localDocumentPairs and selectedPair state
  
  // Set isMounted to false when component unmounts
  useEffect(() => {
    return () => {
      isMounted.current = false;
      // Removed cleanup for custom viewer blob URLs
    };
  }, []); // Removed dependencies related to custom viewer
  
  // Log when view changes
  useEffect(() => {
    console.log("🔍 Active view changed to:", activeView);
    // Removed logic to load images directly for custom viewer
  }, [activeView]);
  
  // Removed the loadPageImages function
  
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
          
          // Construct the full result object (assuming getDocumentPairs returns just the array)
          const fullResult = { id: comparisonId, documentPairs: pairs };
          console.log("Constructed full comparison result:", fullResult);
          
          // Update state
          setComparisonResultData(fullResult); // Store the full result object
          setDocumentPairs(pairs); // Update context (if still needed elsewhere)
          
          // Set flag to prevent refetching
          pairsAlreadyFetched.current = true;
          
          // Auto-select the first matched pair if available
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;
          setSelectedPairIndex(initialIndex); // Set local index state
          setSelectedDocumentPairIndex(initialIndex); // Update context index
                    
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
    console.log("⚡ viewPairComparison called directly for index:", selectedPairIndex);
    
    if (selectedPairIndex === null || !comparisonResultData?.documentPairs?.[selectedPairIndex]) {
      console.error("No valid pair selected at index:", selectedPairIndex);
      return;
    }
    
    // Switch to comparison view immediately
    setActiveView('comparison');
  }, [selectedPairIndex, comparisonResultData]);
  
  // Handle selecting a document pair (only needs index now)
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("🔵 handleSelectDocumentPair called with index:", pairIndex);
    
    if (comparisonResultData?.documentPairs?.[pairIndex]) {
      const pair = comparisonResultData.documentPairs[pairIndex];
      
      // Update the global context and local state for the selected pair index
      setSelectedPairIndex(pairIndex);
      setSelectedDocumentPairIndex(pairIndex); // Update context
      
      // If this came from the "View Comparison" button or selection, switch the view
      if (pair.matched) { // Assuming 'matched' property indicates view switch trigger
        console.log("📱 Valid matched pair selected - switching to comparison view");
        setActiveView('comparison');
      }
    } else {
       console.error("Invalid pair index selected:", pairIndex);
    }
  };
  
  // Function to go back to document matching view
  const backToMatching = useCallback(() => {
    console.log("◀️ Going back to matching view");
    setActiveView('matching');
    // Removed cleanup for custom viewer blob URLs
  }, []); // Removed dependencies

  // Removed getMaxPageCount, goToPreviousPage, goToNextPage functions

  console.log("🔄 Rendering SmartComparisonContainer with activeView:", activeView, 
              "selectedPairIndex:", selectedPairIndex, 
              "hasResultData:", !!comparisonResultData);

  const currentPair = comparisonResultData?.documentPairs?.[selectedPairIndex];

  return (
    <div className="smart-comparison-container">
      {activeView === 'matching' ? (
        <>
          <DocumentMatchingView 
            comparisonId={comparisonId}
            onSelectDocumentPair={handleSelectDocumentPair} // Pass index selection handler
            documentPairs={comparisonResultData?.documentPairs || []} // Pass pairs from full result
            loading={state.loading || fetchInProgress.current || !comparisonResultData} // Loading if fetching or no data yet
            error={state.error}
            selectedPairIndex={selectedPairIndex} // Pass selected index for highlighting
          />
          
          {/* Floating action bar for viewing comparison */}
          {currentPair && currentPair.matched && (
            <div className="view-comparison-bar">
              <button 
                className="view-comparison-button"
                onClick={viewPairComparison} // Already uses selectedPairIndex state
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
            
            {/* Simplified header for SideBySideView */}
            <div className="document-info">
              {currentPair && (
                <>
                  <h3>Document {selectedPairIndex + 1} Comparison</h3>
                  <div className="page-ranges">
                    <span className="base-range">Base: {currentPair.baseStartPage}-{currentPair.baseEndPage}</span>
                    <span className="separator">vs</span>
                    <span className="compare-range">Compare: {currentPair.compareStartPage}-{currentPair.compareEndPage}</span>
                  </div>
                </>
              )}
            </div>
            
            <div className="document-navigation">
              <button 
                className="nav-button prev"
                onClick={() => handleSelectDocumentPair(selectedPairIndex - 1)}
                disabled={selectedPairIndex <= 0}
                style={{ cursor: selectedPairIndex > 0 ? 'pointer' : 'not-allowed' }}
              >
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
                </svg>
                Previous Document
              </button>
              
              <div className="document-counter">
                {selectedPairIndex + 1} / {comparisonResultData?.documentPairs?.length || 0}
              </div>
              
              <button 
                className="nav-button next"
                onClick={() => handleSelectDocumentPair(selectedPairIndex + 1)}
                disabled={!comparisonResultData || selectedPairIndex >= comparisonResultData.documentPairs.length - 1}
                style={{ cursor: comparisonResultData && selectedPairIndex < comparisonResultData.documentPairs.length - 1 ? 'pointer' : 'not-allowed' }}
              >
                Next Document
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
                </svg>
              </button>
            </div>
          </div>
          
          {/* Render SideBySideView with the full result and selected index */}
          {comparisonResultData ? (
            <SideBySideView 
              comparisonId={comparisonId} 
              result={comparisonResultData} // Pass the full result object
              selectedPairIndexProp={selectedPairIndex} // Pass the selected index as a prop
            />
          ) : (
            <div style={{ padding: '20px', textAlign: 'center' }}>
              <Spinner size="large" />
              <p>Loading comparison data...</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default SmartComparisonContainer;
