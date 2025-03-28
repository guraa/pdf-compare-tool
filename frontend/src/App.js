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

const App = () => {
  const [appState, setAppState] = useState({
    stage: 'upload', // 'upload', 'comparing', 'results'
    loading: true,
    error: null,
    config: null
  });

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

  const handleComparisonStart = () => {
    setAppState(prev => ({
      ...prev,
      stage: 'comparing'
    }));
  };

  const handleComparisonComplete = (comparisonId) => {
    setAppState(prev => ({
      ...prev,
      stage: 'results',
      comparisonId
    }));
  };

  const resetComparison = () => {
    setAppState(prev => ({
      ...prev,
      stage: 'upload',
      comparisonId: null
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
                config={appState.config}
              />
            )}
            
            {appState.stage === 'comparing' && (
              <div className="comparison-progress">
                <Spinner size="medium" />
                <h2>Comparing Documents</h2>
                <p>This may take a few moments depending on document size...</p>
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