import React, { useState } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import PDFUploader from './PDFUploader';
import ComparisonSettings from '../comparison/ComparisonSettings';
import { compareDocuments } from '../../services/api';
import './UploadSection.css';

const UploadSection = ({ onComparisonStart }) => {
  const { 
    state, 
    setBaseFile, 
    setCompareFile, 
    setComparisonId, 
    setLoading, 
    setError 
  } = useComparison();
  
  const { preferences } = usePreferences();
  
  const [settings, setSettings] = useState({
    textComparisonMethod: preferences.textComparisonMethod,
    differenceThreshold: preferences.defaultDifferenceThreshold,
    ignoreColors: false,
    ignorePositioning: false,
    compareAnnotations: true,
    compareBookmarks: true,
    compareMetadata: true
  });

  const handleSettingsChange = (name, value) => {
    setSettings(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const startComparison = async () => {
    if (!state.baseFile || !state.compareFile) {
      setError('Please upload both PDF files before starting comparison');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      
      // Notify parent component to update UI state
      onComparisonStart();

      // Call API to start comparison
      const result = await compareDocuments(
        state.baseFile.fileId, 
        state.compareFile.fileId, 
        settings
      );

      // Set the comparison ID in context
      setComparisonId(result.comparisonId);
      
      setLoading(false);
    } catch (err) {
      console.error('Error starting comparison:', err);
      setError(err.response?.data?.error || 'Failed to start comparison. Please try again.');
      setLoading(false);
    }
  };

  return (
    <div className="upload-section">
      <div className="upload-container">
        <h2>Compare PDF Documents</h2>
        <p className="upload-description">
          Upload two PDF documents to identify and analyze differences between them.
        </p>
        
        <div className="upload-grid">
          <div className="upload-item">
            <h3>Base Document</h3>
            <PDFUploader 
              onFileUploaded={setBaseFile} 
              file={state.baseFile}
              label="Upload Base PDF"
            />
          </div>
          
          <div className="upload-item">
            <h3>Comparison Document</h3>
            <PDFUploader 
              onFileUploaded={setCompareFile} 
              file={state.compareFile}
              label="Upload Comparison PDF"
            />
          </div>
        </div>
        
        {(state.baseFile || state.compareFile) && (
          <ComparisonSettings 
            settings={settings}
            onSettingChange={handleSettingsChange}
          />
        )}
        
        {state.error && (
          <div className="upload-error">
            {state.error}
          </div>
        )}
        
        <div className="upload-actions">
          <button 
            className="comparison-button"
            onClick={startComparison}
            disabled={!state.baseFile || !state.compareFile || state.loading}
          >
            {state.loading ? 'Starting Comparison...' : 'Compare Documents'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default UploadSection;