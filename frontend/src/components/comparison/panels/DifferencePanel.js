import React from 'react';
import Spinner from '../../common/Spinner';
import PDFRenderer from '../PDFRenderer';

/**
 * Difference panel component that shows differences in context
 * Improved for better visibility and readability of difference items
 */
const DifferencePanel = ({
  result,
  pageDetails,
  baseContainerRef,
  loading,
  state,
  handleDifferenceSelect,
  showDifferencePanel,
  selectedPairIndex = 0,
  isSmartMode = false
}) => {
  if (!pageDetails) return (
    <div className="loading-container">
      <Spinner size="medium" />
      <p>Loading differences...</p>
    </div>
  );
  
  // Calculate the actual page in base and compare documents
  // Make sure page numbers start at 1, not 0
  let selectedPage = state.selectedPage || 1;
  
  let basePage = selectedPage;
  
  if (isSmartMode && result && result.documentPairs && result.documentPairs.length > 0) {
    const documentPair = result.documentPairs[selectedPairIndex];
    if (documentPair) {
      // Adjust for page offsets in document pairs
      basePage = documentPair.baseStartPage + selectedPage - 1;
    }
  }
  
  // Ensure we never send page 0 to the API
  basePage = Math.max(1, basePage);
  
  // Check if page exists
  const basePageExists = isSmartMode && result && result.documentPairs && result.documentPairs.length > 0 ? 
    (basePage >= result.documentPairs[selectedPairIndex].baseStartPage && 
     basePage <= result.documentPairs[selectedPairIndex].baseEndPage) :
    (result && result.basePageCount >= selectedPage);
  
  // Combine all differences
  const allDifferences = [
    ...(pageDetails.baseDifferences || []),
    ...(pageDetails.compareDifferences || []).filter(diff => 
      !pageDetails.baseDifferences.some(baseDiff => baseDiff.id === diff.id)
    )
  ];

  // Function to get a color for different change types
  const getChangeColor = (changeType) => {
    switch (changeType) {
      case 'added':
        return {
          background: 'rgba(76, 175, 80, 0.15)',
          border: '2px solid #4CAF50',
          textColor: '#1e7b21'
        };
      case 'deleted':
        return {
          background: 'rgba(244, 67, 54, 0.15)',
          border: '2px solid #F44336',
          textColor: '#c41c00'
        };
      case 'modified':
        return {
          background: 'rgba(255, 152, 0, 0.15)',
          border: '2px solid #FF9800',
          textColor: '#c66900'
        };
      default:
        return {
          background: 'rgba(128, 128, 128, 0.15)',
          border: '2px solid #9E9E9E',
          textColor: '#616161'
        };
    }
  };
  
  return (
    <div className={`difference-only-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
      <div className="document-viewer difference-only-viewer">
        <div className="document-header">
          <h3>Difference View</h3>
          <div className="difference-legend">
            <span className="added-legend">Added</span>
            <span className="deleted-legend">Deleted</span>
            <span className="modified-legend">Modified</span>
          </div>
        </div>
        
        <div className="difference-content" ref={baseContainerRef}>
          <div className="difference-renderer" style={{ position: 'relative' }}>
            {basePageExists && (
              <div className="difference-view">
                {/* Base document for reference (with lower opacity) */}
                <div className="reference-layer" style={{ position: 'relative' }}>
                  <PDFRenderer 
                    fileId={state.baseFile?.fileId}
                    page={basePage}
                    zoom={state.viewSettings?.zoom || 1}
                    highlightMode="none"
                    differences={[]}
                    onDifferenceSelect={() => {}}
                    loading={loading}
                    interactive={false}
                    opacity={0.15} // Greatly reduced opacity to make differences more visible
                  />
                </div>
                
                {/* Layer for added content (only in comparison document) */}
                <div className="added-content" style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 10 }}>
                  {allDifferences
                    .filter(diff => diff.changeType === 'added')
                    .map(diff => {
                      const colors = getChangeColor('added');
                      return (
                        <div 
                          key={diff.id}
                          className="diff-item added"
                          style={{
                            position: 'absolute',
                            top: diff.position?.y || 0,
                            left: diff.position?.x || 0,
                            width: Math.max(diff.bounds?.width || 100, 150),
                            minHeight: Math.max(diff.bounds?.height || 30, 40),
                            backgroundColor: colors.background,
                            border: colors.border,
                            borderRadius: '4px',
                            zIndex: 11,
                            cursor: 'pointer',
                            overflow: 'hidden',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.2)'
                          }}
                          onClick={() => handleDifferenceSelect(diff)}
                        >
                          <div className="diff-content" style={{
                            padding: '8px 12px',
                            fontSize: '0.9rem',
                            color: colors.textColor,
                            fontWeight: 500
                          }}>
                            <div className="diff-type" style={{
                              marginBottom: '4px',
                              fontSize: '0.75rem',
                              textTransform: 'uppercase',
                              opacity: 0.8
                            }}>Added ✓</div>
                            <div>{diff.text || diff.description || 'Added content'}</div>
                          </div>
                        </div>
                      );
                    })}
                </div>
                
                {/* Layer for deleted content (only in base document) */}
                <div className="deleted-content" style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 10 }}>
                  {allDifferences
                    .filter(diff => diff.changeType === 'deleted')
                    .map(diff => {
                      const colors = getChangeColor('deleted');
                      return (
                        <div 
                          key={diff.id}
                          className="diff-item deleted"
                          style={{
                            position: 'absolute',
                            top: diff.position?.y || 0,
                            left: diff.position?.x || 0,
                            width: Math.max(diff.bounds?.width || 100, 150),
                            minHeight: Math.max(diff.bounds?.height || 30, 40),
                            backgroundColor: colors.background,
                            border: colors.border,
                            borderRadius: '4px',
                            zIndex: 12,
                            cursor: 'pointer',
                            overflow: 'hidden',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.2)'
                          }}
                          onClick={() => handleDifferenceSelect(diff)}
                        >
                          <div className="diff-content" style={{
                            padding: '8px 12px',
                            fontSize: '0.9rem',
                            color: colors.textColor,
                            fontWeight: 500
                          }}>
                            <div className="diff-type" style={{
                              marginBottom: '4px',
                              fontSize: '0.75rem',
                              textTransform: 'uppercase',
                              opacity: 0.8
                            }}>Deleted ✕</div>
                            <div>{diff.text || diff.description || 'Deleted content'}</div>
                          </div>
                        </div>
                      );
                    })}
                </div>
                
                {/* Layer for modified content */}
                <div className="modified-content" style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 10 }}>
                  {allDifferences
                    .filter(diff => diff.changeType === 'modified')
                    .map(diff => {
                      const colors = getChangeColor('modified');
                      return (
                        <div 
                          key={diff.id}
                          className="diff-item modified"
                          style={{
                            position: 'absolute',
                            top: diff.position?.y || 0,
                            left: diff.position?.x || 0,
                            width: Math.max(diff.bounds?.width || 100, 180),
                            minHeight: Math.max(diff.bounds?.height || 30, 60),
                            backgroundColor: colors.background,
                            border: colors.border,
                            borderRadius: '4px',
                            zIndex: 13,
                            cursor: 'pointer',
                            overflow: 'hidden',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.2)'
                          }}
                          onClick={() => handleDifferenceSelect(diff)}
                        >
                          <div className="diff-content" style={{
                            padding: '8px 12px',
                            fontSize: '0.9rem',
                            color: colors.textColor,
                            fontWeight: 500
                          }}>
                            <div className="diff-type" style={{
                              marginBottom: '4px', 
                              fontSize: '0.75rem',
                              textTransform: 'uppercase',
                              opacity: 0.8
                            }}>Modified ⟳</div>
                            {diff.baseText && diff.compareText ? (
                              <>
                                <div style={{
                                  textDecoration: 'line-through', 
                                  color: '#F44336',
                                  marginBottom: '4px',
                                  fontWeight: 'normal'
                                }}>
                                  {diff.baseText}
                                </div>
                                <div style={{
                                  color: '#4CAF50',
                                  fontWeight: 'normal'
                                }}>
                                  {diff.compareText}
                                </div>
                              </>
                            ) : (
                              diff.description || 'Modified content'
                            )}
                          </div>
                        </div>
                      );
                    })}
                </div>
              </div>
            )}
          </div>
          
          {allDifferences.length === 0 && (
            <div style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              background: 'rgba(255, 255, 255, 0.9)',
              padding: '15px 25px',
              borderRadius: '8px',
              boxShadow: '0 2px 10px rgba(0, 0, 0, 0.1)',
              zIndex: 20,
              textAlign: 'center'
            }}>
              <p style={{ margin: 0 }}>No differences found on this page.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DifferencePanel;