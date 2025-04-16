import React, { useState, useEffect } from 'react';
import { ComparisonProvider } from './context/ComparisonContext';
import { PreferencesProvider } from './context/PreferencesContext';
import MainLayout from './components/layout/MainLayout';
import ComparisonSettings from './components/comparison/ComparisonSettings';
import UploadSection from './components/upload/UploadSection';
import ResultViewer from './components/results/ResultViewer';
import ErrorBoundary from './components/common/ErrorBoundary';
import Spinner from './components/common/Spinner';
import { getAppConfiguration } from './services/api';
import './styles/global.css';

// Add debug logging to help diagnose rendering issues
const enableDebugLogging = () => {
  // Keep a count of renders for each component
  const renderCounts = {};
  
  // Monkey patch React.createElement to add logging
  const originalCreateElement = React.createElement;
  React.createElement = function(type, props, ...children) {
    // Only track component renders, not DOM elements
    if (typeof type === 'function' && type.name) {
      renderCounts[type.name] = (renderCounts[type.name] || 0) + 1;
      
      // Log excessive renders (more than 5 in a short period)
      if (renderCounts[type.name] > 5) {
        console.warn(`⚠️ ${type.name} has rendered ${renderCounts[type.name]} times`);
      }
    }
    return originalCreateElement.apply(this, [type, props, ...children]);
  };
  
  // Reset counts periodically
  setInterval(() => {
    Object.keys(renderCounts).forEach(key => {
      renderCounts[key] = 0;
    });
  }, 5000);
};

// Uncomment to enable debug logging
// enableDebugLogging();

const App = () => {
  const [appState, setAppState] = useState({
    stage: 'upload', // 'upload', 'comparing', 'results'
    loading: true,
    error: null,
    config: null,
    comparisonId: null,
    comparisonStartTime: null
  });

  // Check if API is reachable on load
  useEffect(() => {
    const initializeApp = async () => {
      try {
        // Load app configuration from backend
        const config = await getAppConfiguration();
        setAppState(prev => ({
          ...prev,
          loading: false,
          config
        }));
      } catch (err) {
        console.error('Failed to initialize app:', err);
        setAppState(prev => ({
          ...prev,
          loading: false,
          error: 'Failed to initialize the application. Please reload and try again.'
        }));
      }
    };

    initializeApp();
  }, []);

  // Handle timeout for comparison process
  useEffect(() => {
    // Only run this if we're in the comparing stage
    if (appState.stage !== 'comparing' || !appState.comparisonStartTime) {
      return;
    }

    // Check how long the comparison has been running
    const checkComparisonProgress = () => {
      const now = new Date();
      const elapsedTime = now - appState.comparisonStartTime;
      const maxWaitTime = 180000; // 3 minutes in milliseconds
      
      // If it's been too long, go to results anyway and let the result viewer handle the status
      if (elapsedTime >= maxWaitTime && appState.comparisonId) {
        console.log(`Comparison has been running for ${elapsedTime}ms, proceeding to results view`);
        setAppState(prev => ({
          ...prev,
          stage: 'results'
        }));
      }
    };

    // Set up a check interval
    const intervalId = setInterval(checkComparisonProgress, 10000); // Check every 10 seconds
    return () => clearInterval(intervalId);
  }, [appState.stage, appState.comparisonStartTime, appState.comparisonId]);

  const handleComparisonStart = () => {
    setAppState(prev => ({
      ...prev,
      stage: 'comparing',
      comparisonStartTime: new Date()
    }));
  };

  const handleComparisonComplete = (comparisonId) => {
    console.log("Comparison complete with ID:", comparisonId);
    
    setAppState(prev => ({
      ...prev,
      comparisonId: comparisonId,
      stage: 'results'
    }));
  };

  const resetComparison = () => {
    setAppState(prev => ({
      ...prev,
      stage: 'upload',
      comparisonId: null,
      comparisonStartTime: null
    }));
  };

  // Handle comparison error
  const handleComparisonError = (errorMessage) => {
    setAppState(prev => ({
      ...prev,
      stage: 'upload',
      error: errorMessage
    }));
  };

  // Display loading spinner while initializing
  if (appState.loading) {
    return (
      <div className="app-loading">
        <Spinner size="large" />
        <p>Initializing PDF Comparison Tool...</p>
      </div>
    );
  }

  // Display error message if initialization failed
  if (appState.error) {
    return (
      <div className="app-error">
        <h2>Initialization Error</h2>
        <p>{appState.error}</p>
        <button onClick={() => window.location.reload()}>
          Reload Application
        </button>
      </div>
    );
  }

  return (
    <ErrorBoundary>
      <PreferencesProvider>
        <ComparisonProvider>
          <MainLayout>
            {appState.stage === 'upload' && (
              <UploadSection 
                onComparisonStart={handleComparisonStart}
                onComparisonComplete={handleComparisonComplete}
                onComparisonError={handleComparisonError}
                config={appState.config}
              />
            )}
            
            {appState.stage === 'comparing' && (
              <div className="comparison-progress">
                <Spinner size="medium" />
                <h2>Comparing Documents</h2>
                <p>This may take a few moments depending on document size...</p>
                <p className="comparison-time">
                  {appState.comparisonStartTime && (
                    `Started ${Math.floor((new Date() - appState.comparisonStartTime) / 1000)} seconds ago`
                  )}
                </p>
                <button 
                  className="cancel-button"
                  onClick={resetComparison}
                >
                  Cancel
                </button>
              </div>
            )}
            
            {appState.stage === 'results' && (
              <ResultViewer 
                comparisonId={appState.comparisonId}
                onNewComparison={resetComparison}
              />
            )}
          </MainLayout>
        </ComparisonProvider>
      </PreferencesProvider>
    </ErrorBoundary>
  );
};

export default App;