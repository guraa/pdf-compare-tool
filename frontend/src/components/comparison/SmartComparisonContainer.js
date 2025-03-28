import React, { useState, useEffect } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import SideBySideView from './SideBySideView';
import Spinner from '../common/Spinner';
import { getDocumentPairResult } from '../../services/api';
import './SmartComparisonContainer.css';

const SmartComparisonContainer = ({ comparisonId }) => {
  const { 
    state, 
    setComparisonResult,
    setError,
    setLoading,
    setSelectedPage
  } = useComparison();
  
  const [activeView, setActiveView] = useState('matching'); // 'matching' or 'comparison'
  const [selectedPairIndex, setSelectedPairIndex] = useState(null);
  const [selectedPair, setSelectedPair] = useState(null);
  const [pairResult, setPairResult] = useState(null);
  const [loadingPair, setLoadingPair] = useState(false);
  const [pairError, setPairError] = useState(null);
  
  // Handle selecting a document pair for comparison
  const handleSelectDocumentPair = async (pairIndex, pair) => {
    setSelectedPairIndex(pairIndex);
    setSelectedPair(pair);
    
    // Only load comparison result if the pair is matched
    if (pair.matched) {
      try {
        setLoadingPair(true);
        setPairError(null);
        
        const result = await getDocumentPairResult(comparisonId, pairIndex);
        setPairResult(result);
        setComparisonResult(result);
        
        // Set selected page to the first page of the document
        setSelectedPage(1);
        
        setLoadingPair(false);
      } catch (err) {
        console.error('Error loading document pair result:', err);
        setPairError('Failed to load comparison for this document pair.');
        setLoadingPair(false);
      }
    }
  };
  
  // Function to view the selected pair's comparison
  const viewPairComparison = () => {
    if (selectedPair && selectedPair.matched) {
      setActiveView('comparison');
    }
  };
  
  // Function to go back to document matching view
  const backToMatching = () => {
    setActiveView('matching');
  };

  return (
    <div className="smart-comparison-container">
      {activeView === 'matching' ? (
        <DocumentMatchingView 
          comparisonId={comparisonId}
          onSelectDocumentPair={handleSelectDocumentPair}
        />
      ) : (
        <div className="comparison-view">
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
          
          {loadingPair ? (
            <div className="pair-loading">
              <Spinner size="large" />
              <p>Loading document comparison...</p>
            </div>
          ) : pairError ? (
            <div className="pair-error">
              <div className="error-icon">
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
                </svg>
              </div>
              <h3>Error Loading Comparison</h3>
              <p>{pairError}</p>
              <button 
                className="retry-button" 
                onClick={() => handleSelectDocumentPair(selectedPairIndex, selectedPair)}
              >
                Try Again
              </button>
            </div>
          ) : pairResult ? (
            <div className="pair-comparison">
              <SideBySideView 
                comparisonId={comparisonId}
                result={pairResult}
                documentPair={selectedPair}
              />
            </div>
          ) : (
            <div className="no-pair-selected">
              <p>No document pair selected for comparison.</p>
              <button 
                className="back-button" 
                onClick={backToMatching}
              >
                Back to Document List
              </button>
            </div>
          )}
        </div>
      )}
      
      {selectedPair && selectedPair.matched && activeView === 'matching' && (
        <div className="view-comparison-bar">
          <button 
            className="view-comparison-button"
            onClick={viewPairComparison}
          >
            View Comparison for Document {selectedPairIndex + 1}
          </button>
        </div>
      )}
    </div>
  );
};

export default SmartComparisonContainer;