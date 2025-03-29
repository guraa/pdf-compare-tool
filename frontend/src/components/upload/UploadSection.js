import React, { useState } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import PDFUploader from './PDFUploader';
import ComparisonSettings from '../comparison/ComparisonSettings';
import { compareDocuments, testApiConnection } from '../../services/api';
import './UploadSection.css';

const UploadSection = ({ onComparisonStart, onComparisonComplete, onComparisonError, config }) => {
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
    compareMetadata: true,
    // Smart matching is always enabled - no toggle needed
    smartMatching: true
  });
  
  const [submitting, setSubmitting] = useState(false);

  const handleSettingChange = (name, value) => {
    setSettings(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const startComparison = async () => {
    if (submitting) {
      return; // Prevent multiple submissions
    }
    
    if (!state.baseFile || !state.compareFile) {
      setError('Please upload both PDF files before starting comparison');
      return;
    }

    try {
      setSubmitting(true);
      setLoading(true);
      setError(null);
      
      // Notify parent component to update UI state to "comparing"
      onComparisonStart();

      console.log('Starting comparison with files:', {
        baseFile: state.baseFile,
        compareFile: state.compareFile,
        settings: {
          ...settings,
          smartMatching: true // Always use smart matching
        }
      });

      // Call API to start comparison with smart matching always enabled
      const result = await compareDocuments(
        state.baseFile.fileId, 
        state.compareFile.fileId, 
        {
          ...settings,
          smartMatching: true // Ensure smart matching is enabled
        }
      );
      
      console.log('Comparison result:', result);

      if (!result || !result.comparisonId) {
        throw new Error('Invalid response from server. Missing comparisonId.');
      }

      // Set the comparison ID in context
      setComparisonId(result.comparisonId);
      
      // Notify parent component that comparison is complete and pass the ID
      onComparisonComplete(result.comparisonId);
      
      setLoading(false);
      setSubmitting(false);
    } catch (err) {
      console.error('Error starting comparison:', err);
      const errorMessage = err.response?.data?.error || err.message || 'Failed to start comparison. Please try again.';
      
      setError(errorMessage);
      setLoading(false);
      setSubmitting(false);
      
      // Notify parent of the error
      if (onComparisonError) {
        onComparisonError(errorMessage);
      }
      
      // Reset UI state since comparison failed
      onComparisonStart(false);
    }
  };

  return (
    <div className="upload-section">
      <div className="upload-container">
        <h2>Compare PDF Documents</h2>
        <p className="upload-description">
          Upload two PDF documents to identify and analyze differences between them.
          Smart Document Matching is enabled to handle multi-document PDFs automatically.
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
            onSettingChange={handleSettingChange}
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
            disabled={!state.baseFile || !state.compareFile || state.loading || submitting}
          >
            {submitting ? 'Starting Comparison...' : 'Compare Documents'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default UploadSection;