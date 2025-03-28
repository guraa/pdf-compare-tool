import React from 'react';
import './DifferenceStats.css';

const DifferenceStats = ({ result, onDifferenceClick }) => {
  if (!result) return null;
  
  // Find the first occurrence of each type of difference
  const findFirstDifferenceOfType = (type) => {
    if (!result.pageDifferences) return null;
    
    for (const page of result.pageDifferences) {
      switch (type) {
        case 'text':
          if (page.textDifferences?.differences?.length > 0) {
            return { page: page.pageNumber, type: 'text', index: 0 };
          }
          break;
        case 'image':
          if (page.imageDifferences?.length > 0) {
            return { page: page.pageNumber, type: 'image', index: 0 };
          }
          break;
        case 'font':
          if (page.fontDifferences?.length > 0) {
            return { page: page.pageNumber, type: 'font', index: 0 };
          }
          break;
        case 'style':
          if (page.textElementDifferences?.some(diff => diff.styleDifferent)) {
            const index = page.textElementDifferences.findIndex(diff => diff.styleDifferent);
            return { page: page.pageNumber, type: 'style', index };
          }
          break;
        default:
          return null;
      }
    }
    
    return null;
  };
  
  // Calculate difference breakdown
  const calculateTextDifferenceBreakdown = () => {
    if (!result.pageDifferences) return { added: 0, deleted: 0, modified: 0 };
    
    const breakdown = { added: 0, deleted: 0, modified: 0 };
    
    result.pageDifferences.forEach(page => {
      if (page.textDifferences?.differences) {
        page.textDifferences.differences.forEach(diff => {
          if (diff.differenceType === 'ADDED') {
            breakdown.added++;
          } else if (diff.differenceType === 'DELETED') {
            breakdown.deleted++;
          } else if (diff.differenceType === 'MODIFIED') {
            breakdown.modified++;
          }
        });
      }
    });
    
    return breakdown;
  };
  
  const textBreakdown = calculateTextDifferenceBreakdown();
  
  const categories = [
    { 
      type: 'text', 
      label: 'Text Content', 
      count: result.totalTextDifferences || 0,
      breakdown: textBreakdown,
      firstDiff: findFirstDifferenceOfType('text')
    },
    { 
      type: 'image', 
      label: 'Images', 
      count: result.totalImageDifferences || 0,
      firstDiff: findFirstDifferenceOfType('image')
    },
    { 
      type: 'font', 
      label: 'Fonts', 
      count: result.totalFontDifferences || 0,
      firstDiff: findFirstDifferenceOfType('font')
    },
    { 
      type: 'style', 
      label: 'Text Styles', 
      count: result.totalStyleDifferences || 0,
      firstDiff: findFirstDifferenceOfType('style')
    }
  ];

  return (
    <div className="difference-stats">
      <h4>Difference Breakdown</h4>
      
      <div className="stats-grid">
        {categories.map(category => (
          <div className="stat-category" key={category.type}>
            <div className="category-header">
              <div className={`category-icon ${category.type}`}>
                {category.type === 'text' && (
                  <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                    <path d="M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z" />
                  </svg>
                )}
                {category.type === 'image' && (
                  <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                    <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
                  </svg>
                )}
                {category.type === 'font' && (
                  <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                    <path d="M9.93 13.5h4.14L12 7.98zM20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-4.05 16.5l-1.14-3H9.17l-1.12 3H5.96l5.11-13h1.86l5.11 13h-2.09z" />
                  </svg>
                )}
                {category.type === 'style' && (
                  <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                    <path d="M2.53 19.65l1.34.56v-9.03l-2.43 5.86c-.41 1.02.08 2.19 1.09 2.61zm19.5-3.7L17.07 3.98c-.31-.75-1.04-1.21-1.81-1.23-.26 0-.53.04-.79.15L7.1 5.95c-.75.31-1.21 1.03-1.23 1.8-.01.27.04.54.15.8l4.96 11.97c.31.76 1.05 1.22 1.83 1.23.26 0 .52-.05.77-.15l7.36-3.05c1.02-.42 1.51-1.59 1.09-2.6zm-9.2 3.8L7.87 7.79l7.35-3.04h.01l4.95 11.95-7.35 3.05z" />
                  </svg>
                )}
              </div>
              <div className="category-info">
                <div className="category-label">{category.label}</div>
                <div className="category-count">{category.count} differences</div>
              </div>
            </div>
            
            {category.count > 0 && (
              <div className="category-details">
                {category.type === 'text' && category.breakdown && (
                  <div className="text-breakdown">
                    <div className="breakdown-item">
                      <div className="breakdown-label">Added</div>
                      <div className="breakdown-value added">{category.breakdown.added}</div>
                    </div>
                    <div className="breakdown-item">
                      <div className="breakdown-label">Deleted</div>
                      <div className="breakdown-value deleted">{category.breakdown.deleted}</div>
                    </div>
                    <div className="breakdown-item">
                      <div className="breakdown-label">Modified</div>
                      <div className="breakdown-value modified">{category.breakdown.modified}</div>
                    </div>
                  </div>
                )}
                
                {category.firstDiff && (
                  <button 
                    className="view-differences-button"
                    onClick={() => onDifferenceClick(category.firstDiff)}
                  >
                    View Differences
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default DifferenceStats;