import React, { useRef } from 'react';
import PDFRenderer from '../PDFRenderer';

/**
 * Overlay panel component that displays two PDFs overlaid with transparency
 * Fixed to ensure proper rendering and positioning
 */
const OverlayPanel = ({
  result,
  pageDetails,
  loading,
  state,
  handleDifferenceSelect,
  showDifferencePanel,
  overlayOpacity
}) => {
  const overlayContainerRef = useRef(null);

  return (
    <div className={`overlay-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
      <div className="document-viewer overlay-viewer">
        <div className="document-header">
          <h3>Overlay View</h3>
          <div className="overlay-legend">
            <span className="base-legend">Base Document</span>
            <span className="compare-legend">Comparison Document</span>
          </div>
        </div>
        
        <div 
          className="overlay-content" 
          ref={overlayContainerRef}
        >
          <div className="overlay-layers">
            {/* Base document layer */}
            <div className="base-layer">
              {result?.basePageCount >= state.selectedPage && (
                <PDFRenderer 
                  fileId={state.baseFile?.fileId}
                  page={state.selectedPage}
                  zoom={state.viewSettings?.zoom || 1}
                  highlightMode="none"
                  differences={[]}
                  selectedDifference={null}
                  onDifferenceSelect={() => {}}
                  loading={loading}
                  interactive={false}
                />
              )}
            </div>
            
            {/* Compare document layer with opacity */}
            <div 
              className="compare-layer" 
              style={{ opacity: overlayOpacity }}
            >
              {result?.comparePageCount >= state.selectedPage && (
                <PDFRenderer 
                  fileId={state.compareFile?.fileId}
                  page={state.selectedPage}
                  zoom={state.viewSettings?.zoom || 1}
                  highlightMode="none"
                  differences={[]}
                  selectedDifference={null}
                  onDifferenceSelect={() => {}}
                  loading={loading}
                  interactive={false}
                />
              )}
            </div>
            
            {/* Highlight differences */}
            {pageDetails && (
              <div className="difference-overlays">
                {pageDetails.baseDifferences && pageDetails.baseDifferences.map(diff => (
                  <div 
                    key={diff.id}
                    className="difference-highlight"
                    style={{
                      position: 'absolute',
                      top: diff.position?.y || 0,
                      left: diff.position?.x || 0,
                      width: diff.bounds?.width || 100,
                      height: diff.bounds?.height || 30,
                      backgroundColor: 'rgba(255, 0, 0, 0.3)',
                      border: '2px solid rgba(255, 0, 0, 0.5)',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      zIndex: 50
                    }}
                    onClick={() => handleDifferenceSelect(diff)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default OverlayPanel;