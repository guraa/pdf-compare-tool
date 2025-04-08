import React, { useState, useEffect } from 'react';
import './EnhancedDifferenceList.css';

/**
 * Enhanced difference list component that displays differences in a tree view
 * similar to the i-net PDFC interface
 */
const EnhancedDifferenceList = ({ differences, onDifferenceSelect, selectedDifference }) => {
  const [groupedDifferences, setGroupedDifferences] = useState({});
  const [expandedGroups, setExpandedGroups] = useState({});
  const [totalCount, setTotalCount] = useState(0);

  // Process and group differences when they change
  useEffect(() => {
    if (!differences) return;
    
    // Combine all differences from base and compare into one array
    const combinedDiffs = [...(differences.baseDifferences || []), ...(differences.compareDifferences || [])];
    
    // Remove duplicates by ID
    const uniqueDiffs = [];
    const seenIds = new Set();
    
    combinedDiffs.forEach(diff => {
      if (!seenIds.has(diff.id)) {
        seenIds.add(diff.id);
        uniqueDiffs.push(diff);
      }
    });
    
    // Group differences by type
    const grouped = uniqueDiffs.reduce((acc, diff) => {
      const type = diff.type || 'other';
      if (!acc[type]) {
        acc[type] = [];
      }
      acc[type].push(diff);
      return acc;
    }, {});
    
    // Set initial expanded state for groups
    const expanded = {};
    Object.keys(grouped).forEach(group => {
      expanded[group] = true; // Default to expanded
    });
    
    setGroupedDifferences(grouped);
    setExpandedGroups(expanded);
    setTotalCount(uniqueDiffs.length);
  }, [differences]);

  // Handle toggling group expansion
  const toggleGroup = (group) => {
    setExpandedGroups(prev => ({
      ...prev,
      [group]: !prev[group]
    }));
  };

  // Get icon for difference type
  const getDifferenceTypeIcon = (type) => {
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
      case 'structure':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z" />
          </svg>
        );
      default:
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z" />
          </svg>
        );
    }
  };

  // Get change type indicator
  const getChangeTypeIndicator = (changeType) => {
    switch (changeType) {
      case 'added':
        return <span className="change-type added">Added</span>;
      case 'deleted':
        return <span className="change-type deleted">Deleted</span>;
      case 'modified':
        return <span className="change-type modified">Modified</span>;
      default:
        return null;
    }
  };

  // Get human-readable group name
  const getGroupName = (group) => {
    switch (group) {
      case 'text':
        return 'Text Content';
      case 'image':
        return 'Images';
      case 'font':
        return 'Fonts';
      case 'style':
        return 'Styles';
      case 'structure':
        return 'Structure';
      default:
        return group.charAt(0).toUpperCase() + group.slice(1);
    }
  };

  // Format difference description
  const getDifferenceDescription = (diff) => {
    if (diff.description) {
      return diff.description;
    }
    
    switch (diff.type) {
      case 'text':
        if (diff.baseText && diff.compareText) {
          return `"${diff.baseText.substring(0, 20)}${diff.baseText.length > 20 ? '...' : ''}" → "${diff.compareText.substring(0, 20)}${diff.compareText.length > 20 ? '...' : ''}"`;
        } else if (diff.baseText) {
          return `"${diff.baseText.substring(0, 30)}${diff.baseText.length > 30 ? '...' : ''}"`;
        } else if (diff.compareText) {
          return `"${diff.compareText.substring(0, 30)}${diff.compareText.length > 30 ? '...' : ''}"`;
        } else {
          return 'Text content changed';
        }
      case 'image':
        return diff.imageName || 'Image changed';
      case 'font':
        return diff.fontName || 'Font changed';
      case 'style':
        return 'Style properties changed';
      default:
        return 'Difference detected';
    }
  };

  return (
    <div className="enhanced-difference-list">
      <div className="difference-header">
        <h3>Differences</h3>
        <span className="difference-count">{totalCount}</span>
      </div>
      
      {totalCount === 0 ? (
        <div className="no-differences">
          <p>No differences found on this page.</p>
        </div>
      ) : (
        <div className="difference-groups">
          {Object.entries(groupedDifferences).map(([group, diffs]) => (
            <div key={group} className="difference-group">
              <div 
                className="group-header" 
                onClick={() => toggleGroup(group)}
              >
                <div className="group-toggle">
                  {expandedGroups[group] ? '▼' : '►'}
                </div>
                <div className={`group-icon ${group}`}>
                  {getDifferenceTypeIcon(group)}
                </div>
                <div className="group-title">
                  {getGroupName(group)}
                  <span className="group-count">{diffs.length}</span>
                </div>
              </div>
              
              {expandedGroups[group] && (
                <div className="group-items">
                  {diffs.map(diff => (
                    <div 
                      key={diff.id} 
                      className={`difference-item ${selectedDifference && selectedDifference.id === diff.id ? 'selected' : ''}`}
                      onClick={() => onDifferenceSelect(diff)}
                    >
                      <div className="item-content">
                        <div className="item-description">
                          {getDifferenceDescription(diff)}
                        </div>
                        {getChangeTypeIndicator(diff.changeType)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default EnhancedDifferenceList;