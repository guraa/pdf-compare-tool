import React, { useState, useEffect } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { getDocumentPairs, getComparisonResult } from '../../services/api';
import DocumentMatchingView from './DocumentMatchingView';
import PDFComparisonView from './PDFComparisonView';
import Spinner from '../common/Spinner';
import './PDFComparisonApp.css';

/**
 * Main PDF Comparison Application - combines document matching and detailed comparison views
 * Mimics i-net PDFC functionality with a modern React approach
 */
const PDFComparisonApp = ({ comparisonId }) => {
  // Use global context for state management
  const { 
    state, 
    setDocumentPairs, 
    setSelectedDocumentPairIndex,
    setError,
    setLoading
  } = useComparison();
  
  // Local state
  const [activeView, setActiveView] = useState('documentMatching');
  const [localDocumentPairs, setLocalDocumentPairs] = useState([]);
  const [selectedPairIndex, setSelectedPairIndex] = useState(null);
  const [currentResult, setCurrentResult] = useState(null);
  
  // Load comparison result when component mounts
  useEffect(() => {
    const loadComparisonResult = async () => {
      if (!comparisonId) return;
      
      try {
        setLoading(true);
        
        // Get the full comparison result
        const result = await getComparisonResult(comparisonId);
        
        if (!result) {
          throw new Error("Failed to load comparison result");
        }
        
        setCurrentResult(result);
        setLoading(false);
      } catch (error) {
        console.error("Error loading comparison result:", error);
        setError("Failed to load comparison result. Please try again.");
        setLoading(false);
      }
    };
    
    loadComparisonResult();
  }, [comparisonId, setError, setLoading]);
  
  // Load document pairs when component mounts
  useEffect(() => {
    const loadDocumentPairs = async () => {
      if (!comparisonId) return;
      
      try {
        setLoading(true);
        
        // Get the list of document pairs
        const pairs = await getDocumentPairs(comparisonId);
        
        if (pairs && pairs.length > 0) {
          console.log(`Loaded ${pairs.length} document pairs`);
          
          // Update state
          setLocalDocumentPairs(pairs);
          setDocumentPairs(pairs);
          
          // Auto-select the first matched pair
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          if (matchedPairIndex >= 0) {
            setSelectedPairIndex(matchedPairIndex);
            setSelectedDocumentPairIndex(matchedPairIndex);
          } else {
            setSelectedPairIndex(0);
            setSelectedDocumentPairIndex(0);
          }
          
          setLoading(false);
        } else {
          throw new Error("No document pairs found");
        }
      } catch (error) {
        console.error("Error loading document pairs:", error);
        setError("Failed to load document matches. Please try again.");
        setLoading(false);
      }
    };
    
    loadDocumentPairs();
  }, [comparisonId, setDocumentPairs, setSelectedDocumentPairIndex, setError, setLoading]);
  
  // Handle selecting a document pair
  const handleSelectDocumentPair = (pairIndex, pair) => {
    if (!pair) return;
    
    console.log(`Selected document pair at index ${pairIndex}`);
    
    // Update state
    setSelectedPairIndex(pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    
    // Switch to comparison view if the pair is matched
    if (pair.matched) {
      setActiveView('comparison');
    }
  };
  
  // Back to document matching view
  const backToDocumentMatching = () => {
    setActiveView('documentMatching');
  };
  
  // Loading state
  if (state.loading && localDocumentPairs.length === 0) {
    return (
      <div className="app-loading">
        <Spinner size="large" />
        <p>Loading comparison data...</p>
      </div>
    );
  }
  
  // Error state
  if (state.error && localDocumentPairs.length === 0) {
    return (
      <div className="app-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Error Loading Comparison</h3>
        <p>{state.error}</p>
        <button 
          className="retry-button"
          onClick={() => window.location.reload()}
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="pdf-comparison-app">
      {activeView === 'documentMatching' ? (
        <>
          <DocumentMatchingView 
            comparisonId={comparisonId}
            onSelectDocumentPair={handleSelectDocumentPair}
          />
          
          {selectedPairIndex !== null && localDocumentPairs[selectedPairIndex]?.matched && (
            <div className="view-comparison-bar">
              <button 
                className="view-comparison-button"
                onClick={() => setActiveView('comparison')}
              >
                View Comparison for Document {selectedPairIndex + 1}
              </button>
            </div>
          )}
        </>
      ) : (
        <div className="comparison-view-container">
          <div className="comparison-header-bar">
            <button 
              className="back-button"
              onClick={backToDocumentMatching}
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
              </svg>
              Back to Document List
            </button>
            
            <div className="current-document-info">
              {selectedPairIndex !== null && (
                <h3>Document {selectedPairIndex + 1} Comparison</h3>
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
              >
                Next Document
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
                </svg>
              </button>
            </div>
          </div>
          
          <div className="comparison-main-content">
            {selectedPairIndex !== null && localDocumentPairs[selectedPairIndex] ? (
              <PDFComparisonView 
                comparisonId={comparisonId}
                documentPair={localDocumentPairs[selectedPairIndex]} 
              />
            ) : (
              <div className="no-document-selected">
                <p>No document pair selected</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default PDFComparisonApp;