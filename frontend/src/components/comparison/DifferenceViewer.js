import React, { useState } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import Spinner from '../common/Spinner';
import './DifferenceViewer.css';

const DifferenceViewer = ({ 
  pageDetails, 
  selectedDifference, 
  onDifferenceSelect, 
  loading = false 
}) => {
  const { preferences } = usePreferences();
  const [filter, setFilter] = useState('all');
  
  // Get all differences combined from page details
  const getAllDifferences = () => {
    if (!pageDetails) return [];
    
    const baseDiffs = pageDetails.baseDifferences || [];
    const compareDiffs = pageDetails.compareDifferences || [];
    
    // Combine and deduplicate differences
    const allDiffs = [...baseDiffs];
    
    // Add compare-only differences
    compareDiffs.forEach(diff => {
      if (!allDiffs.some(baseDiff => baseDiff.id === diff.id)) {
        allDiffs.push(diff);
      }
    });
    
    return allDiffs;
  };
  
  const differences = getAllDifferences();
  
  // Filter differences by type
  const getFilteredDifferences = () => {
    if (filter === 'all') return differences;
    return differences.filter(diff => diff.type === filter);
  };
  
  const filteredDifferences = getFilteredDifferences();
  
  // Get type icon
  const getTypeIcon = (type) => {
    switch (type) {
      case 'text':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z" />
          </svg>
        );
      case 'image':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
          </svg>
        );
      case 'font':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M9.93 13.5h4.14L12 7.98zM20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-4.05 16.5l-1.14-3H9.17l-1.12 3H5.96l5.11-13h1.86l5.11 13h-2.09z" />
          </svg>
        );
      case 'style':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M2.53 19.65l1.34.56v-9.03l-2.43 5.86c-.41 1.02.08 2.19 1.09 2.61zm19.5-3.7L17.07 3.98c-.31-.75-1.04-1.21-1.81-1.23-.26 0-.53.04-.79.15L7.1 5.95c-.75.31-1.21 1.03-1.23 1.8-.01.27.04.54.15.8l4.96 11.97c.31.76 1.05 1.22 1.83 1.23.26 0 .52-.05.77-.15l7.36-3.05c1.02-.42 1.51-1.59 1.09-2.6z" />
          </svg>
        );
      case 'metadata':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
          </svg>
        );
      default:
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z" />
          </svg>
        );
    }
  };
  
  // Get change type icon/label
  const getChangeTypeLabel = (diff) => {
    if (diff.changeType === 'added') {
      return (
        <span className="change-type added">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
          </svg>
          Added
        </span>
      );
    } else if (diff.changeType === 'deleted') {
      return (
        <span className="change-type deleted">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M19 13H5v-2h14v2z" />
          </svg>
          Deleted
        </span>
      );
    } else if (diff.changeType === 'modified') {
      return (
        <span className="change-type modified">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" />
          </svg>
          Modified
        </span>
      );
    }
    
    return null;
  };
  
  // Render difference details based on type
  const renderDifferenceDetails = (diff) => {
    switch (diff.type) {
      case 'text':
        return (
          <div className="difference-text-details">
            {diff.baseText && (
              <div className="text-comparison">
                <div className="comparison-label">Base Text:</div>
                <div className="comparison-value base">{diff.baseText}</div>
              </div>
            )}
            
            {diff.compareText && (
              <div className="text-comparison">
                <div className="comparison-label">Compare Text:</div>
                <div className="comparison-value compare">{diff.compareText}</div>
              </div>
            )}
          </div>
        );
      
      case 'image':
        return (
          <div className="difference-image-details">
            {diff.imageName && (
              <div className="image-name">
                <span className="detail-label">Image:</span>
                <span className="detail-value">{diff.imageName}</span>
              </div>
            )}
            
            <div className="image-properties">
              {diff.dimensions && (
                <div className="image-dimension">
                  <span className="detail-label">Size:</span>
                  <span className="detail-value">
                    {diff.dimensions.width} × {diff.dimensions.height} px
                  </span>
                </div>
              )}
              
              {diff.format && (
                <div className="image-format">
                  <span className="detail-label">Format:</span>
                  <span className="detail-value">{diff.format}</span>
                </div>
              )}
            </div>
            
            {diff.changes && diff.changes.length > 0 && (
              <div className="image-changes">
                <span className="detail-label">Changes:</span>
                <ul className="changes-list">
                  {diff.changes.map((change, index) => (
                    <li key={index}>{change}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        );
      
      case 'font':
        return (
          <div className="difference-font-details">
            {diff.fontName && (
              <div className="font-name">
                <span className="detail-label">Font:</span>
                <span className="detail-value">{diff.fontName}</span>
              </div>
            )}
            
            {diff.fontFamily && (
              <div className="font-family">
                <span className="detail-label">Family:</span>
                <span className="detail-value">{diff.fontFamily}</span>
              </div>
            )}
            
            {diff.changes && diff.changes.length > 0 && (
              <div className="font-changes">
                <span className="detail-label">Changes:</span>
                <ul className="changes-list">
                  {diff.changes.map((change, index) => (
                    <li key={index}>{change}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        );
      
      case 'style':
        return (
          <div className="difference-style-details">
            {diff.text && (
              <div className="style-text">
                <span className="detail-label">Text:</span>
                <span className="detail-value">{diff.text}</span>
              </div>
            )}
            
            {diff.baseStyle && diff.compareStyle && (
              <div className="style-comparison">
                <div className="style-base">
                  <span className="detail-label">Base Style:</span>
                  <div className="style-value">
                    {Object.entries(diff.baseStyle).map(([key, value]) => (
                      <div className="style-property" key={key}>
                        <span className="property-name">{key}:</span>
                        <span className="property-value">{value}</span>
                      </div>
                    ))}
                  </div>
                </div>
                
                <div className="style-compare">
                  <span className="detail-label">Compare Style:</span>
                  <div className="style-value">
                    {Object.entries(diff.compareStyle).map(([key, value]) => (
                      <div className="style-property" key={key}>
                        <span className="property-name">{key}:</span>
                        <span className="property-value">{value}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        );
      
      case 'metadata':
        return (
          <div className="difference-metadata-details">
            <div className="metadata-key">
              <span className="detail-label">Property:</span>
              <span className="detail-value">{diff.key}</span>
            </div>
            
            {diff.baseValue !== undefined && (
              <div className="metadata-base">
                <span className="detail-label">Base Value:</span>
                <span className="detail-value">
                  {diff.baseValue || <em>Empty</em>}
                </span>
              </div>
            )}
            
            {diff.compareValue !== undefined && (
              <div className="metadata-compare">
                <span className="detail-label">Compare Value:</span>
                <span className="detail-value">
                  {diff.compareValue || <em>Empty</em>}
                </span>
              </div>
            )}
          </div>
        );
      
      default:
        return (
          <div className="difference-generic-details">
            <p>Difference found in {diff.type}.</p>
          </div>
        );
    }
  };

  return (
    <div className="difference-viewer">
      <div className="difference-viewer-header">
        <h3>Differences</h3>
        
        <div className="filter-controls">
          <select 
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            <option value="all">All Types</option>
            <option value="text">Text</option>
            <option value="image">Images</option>
            <option value="font">Fonts</option>
            <option value="style">Styles</option>
            <option value="metadata">Metadata</option>
          </select>
        </div>
      </div>
      
      <div className="difference-count">
        {filteredDifferences.length} difference{filteredDifferences.length !== 1 ? 's' : ''} found
      </div>
      
      {loading ? (
        <div className="difference-viewer-loading">
          <Spinner size="small" />
          <span>Loading differences...</span>
        </div>
      ) : filteredDifferences.length === 0 ? (
        <div className="no-differences">
          <p>No differences found matching the current filter.</p>
        </div>
      ) : (
        <div className="difference-list">
          {filteredDifferences.map((diff) => (
            <div 
              key={diff.id}
              className={`difference-item ${diff.type} ${selectedDifference?.id === diff.id ? 'selected' : ''}`}
              onClick={() => onDifferenceSelect(diff)}
            >
              <div className="difference-header">
                <div className="difference-type">
                  <span className={`type-icon ${diff.type}`}>
                    {getTypeIcon(diff.type)}
                  </span>
                  <span className="type-label">{diff.type.charAt(0).toUpperCase() + diff.type.slice(1)}</span>
                </div>
                
                {getChangeTypeLabel(diff)}
              </div>
              
              <div className="difference-content">
                {renderDifferenceDetails(diff)}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default DifferenceViewer;