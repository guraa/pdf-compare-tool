import React, { useState, useEffect, useRef } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import './PDFRenderer.css';

// Import pdfjsLib if using in browser
import * as pdfjsLib from 'pdfjs-dist';
import pdfjsWorker from 'pdfjs-dist/build/pdf.worker.entry';

// Set worker path
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorker;

const PDFRenderer = ({ 
  fileId, 
  page, 
  zoom = 1, 
  highlightMode = 'all',
  differences = [],
  selectedDifference = null,
  onDifferenceSelect,
  loading = false,
  interactive = true,
  opacity = 1
}) => {
  const [pdfData, setPdfData] = useState(null);
  const [rendered, setRendered] = useState(false);
  const [renderError, setRenderError] = useState(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [isImage, setIsImage] = useState(false);
  const [hoveredDiffId, setHoveredDiffId] = useState(null);
  
  const { preferences } = usePreferences();
  const canvasRef = useRef(null);
  const highlightLayerRef = useRef(null);
  const imageRef = useRef(null);
  const tooltipRef = useRef(null);
  
  // Fetch PDF page data
  useEffect(() => {
    const fetchPdfPage = async () => {
      if (!fileId || !page) return;
      
      try {
        // Get the PDF page as a blob
        const response = await fetch(`/api/pdfs/document/${fileId}/page/${page}`);
        
        if (!response.ok) {
          throw new Error(`Failed to fetch page: ${response.status} ${response.statusText}`);
        }
        
        const contentType = response.headers.get('content-type');
        const isImageType = contentType && contentType.startsWith('image/');
        setIsImage(isImageType);
        
        // Create a URL for the blob
        const blob = await response.blob();
        const pageUrl = URL.createObjectURL(blob);
        
        setPdfData(pageUrl);
        setRenderError(null);
      } catch (err) {
        console.error('Error fetching PDF page:', err);
        setRenderError('Failed to load page: ' + err.message);
      }
    };
    
    fetchPdfPage();
    
    // Cleanup function to revoke the blob URL
    return () => {
      if (pdfData) {
        URL.revokeObjectURL(pdfData);
      }
    };
  }, [fileId, page]);
  
  // Handle image rendering
  useEffect(() => {
    if (isImage && pdfData && imageRef.current) {
      const image = imageRef.current;
      image.onload = () => {
        setDimensions({
          width: image.naturalWidth * zoom,
          height: image.naturalHeight * zoom
        });
        setRendered(true);
        setRenderError(null);
      };
      
      image.onerror = (err) => {
        console.error('Error loading image:', err);
        setRenderError('Failed to load image');
        setRendered(false);
      };
      
      image.src = pdfData;
    }
  }, [pdfData, isImage, zoom]);
  
  // Render PDF page to canvas
  useEffect(() => {
    const renderPdf = async () => {
      if (!pdfData || !canvasRef.current || isImage) return;
      
      try {
        setRendered(false);
        
        // Load the PDF document
        const loadingTask = pdfjsLib.getDocument({
          url: pdfData,
          cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@2.16.105/cmaps/',
          cMapPacked: true,
        });
        
        const pdfDoc = await loadingTask.promise;
        
        // Get the specified page
        const pdfPage = await pdfDoc.getPage(1); // Always get first page since we're loading individual pages
        
        // Calculate scale based on zoom
        const viewport = pdfPage.getViewport({ scale: zoom });
        
        // Set canvas dimensions
        const canvas = canvasRef.current;
        const context = canvas.getContext('2d');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        
        // Store dimensions for highlight layer
        setDimensions({
          width: viewport.width,
          height: viewport.height
        });
        
        // Render the page
        const renderContext = {
          canvasContext: context,
          viewport: viewport
        };
        
        await pdfPage.render(renderContext).promise;
        
        setRendered(true);
        setRenderError(null);
      } catch (err) {
        console.error('Error rendering PDF:', err);
        setRenderError('Failed to render PDF page');
      }
    };
    
    renderPdf();
  }, [pdfData, zoom, isImage]);
  
  // Draw difference highlights
  useEffect(() => {
    if (!highlightLayerRef.current || !rendered || highlightMode === 'none' || differences.length === 0) {
      return;
    }
    
    const canvas = highlightLayerRef.current;
    const ctx = canvas.getContext('2d');
    
    // Clear the canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Set canvas size to match PDF
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    // Draw highlights based on mode
    differences.forEach(diff => {
      // Skip if the type doesn't match the highlight mode
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Get highlight color based on difference type
      const color = getHighlightColor(diff.type, diff.changeType);
      
      // Check if this is the selected difference
      const isSelected = selectedDifference && 
        selectedDifference.id === diff.id;
      
      // Check if this is being hovered
      const isHovered = hoveredDiffId === diff.id;
      
      // Draw the highlight
      drawHighlight(ctx, diff, color, isSelected, isHovered);
    });
  }, [differences, highlightMode, rendered, dimensions, selectedDifference, hoveredDiffId, preferences.differenceColors]);
  
  // Update tooltip position when hovering over a difference
  useEffect(() => {
    if (!tooltipRef.current || !hoveredDiffId) return;
    
    const tooltip = tooltipRef.current;
    const hoveredDiff = differences.find(d => d.id === hoveredDiffId);
    
    if (hoveredDiff && hoveredDiff.bounds) {
      const { x, y, width } = hoveredDiff.bounds;
      
      // Position tooltip above the difference
      tooltip.style.left = `${x + width/2}px`;
      tooltip.style.top = `${y - 30}px`;
      tooltip.style.display = 'block';
    } else {
      tooltip.style.display = 'none';
    }
  }, [hoveredDiffId, differences]);
  
  // Draw highlight rectangle or outline
  const drawHighlight = (ctx, diff, color, isSelected, isHovered) => {
    if (!diff.bounds) return;
    
    const { x, y, width, height } = diff.bounds;
    
    // Set highlight style
    ctx.fillStyle = color;
    ctx.strokeStyle = isSelected 
      ? 'rgba(255, 255, 0, 0.8)' 
      : isHovered 
        ? 'rgba(255, 255, 255, 0.8)' 
        : color.replace('0.3', '0.8');
    
    ctx.lineWidth = isSelected ? 3 : isHovered ? 2 : 1;
    
    // Draw the highlight
    ctx.fillRect(x, y, width, height);
    ctx.strokeRect(x, y, width, height);
    
    // If selected, add an indicator
    if (isSelected) {
      // Add a glow effect
      ctx.shadowColor = 'rgba(255, 255, 0, 0.8)';
      ctx.shadowBlur = 10;
      ctx.beginPath();
      ctx.arc(x + width / 2, y - 10, 5, 0, 2 * Math.PI);
      ctx.fill();
      ctx.shadowBlur = 0;
      
      // Add a label if available
      if (diff.type || diff.changeType) {
        ctx.font = '10px Arial';
        ctx.fillStyle = 'black';
        ctx.textAlign = 'center';
        const label = `${diff.type?.toUpperCase() || ''} ${diff.changeType?.toUpperCase() || ''}`;
        ctx.fillText(label.trim(), x + width / 2, y - 15);
      }
    }
    
    // If hovered, draw an icon or indicator
    if (isHovered && !isSelected) {
      ctx.beginPath();
      ctx.arc(x + width / 2, y - 5, 3, 0, 2 * Math.PI);
      ctx.fillStyle = 'white';
      ctx.fill();
      ctx.strokeStyle = 'black';
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  };
  
  // Get color for highlight based on difference type and change type
  const getHighlightColor = (type, changeType) => {
    // Use colors from preferences if available
    if (preferences.differenceColors && preferences.differenceColors[type]) {
      let baseColor = preferences.differenceColors[type];
      
      // Modify alpha based on change type
      switch(changeType) {
        case 'added':
          return baseColor.replace(')', ', 0.5)').replace('rgb', 'rgba');
        case 'deleted':
          return baseColor.replace(')', ', 0.5)').replace('rgb', 'rgba');
        case 'modified':
          return baseColor.replace(')', ', 0.5)').replace('rgb', 'rgba');
        default:
          return baseColor.replace(')', ', 0.3)').replace('rgb', 'rgba');
      }
    }
    
 switch (type) {
      case 'text':
        return changeType === 'added' ? 'rgba(76, 175, 80, 0.3)' : 
               changeType === 'deleted' ? 'rgba(244, 67, 54, 0.3)' : 
               'rgba(255, 152, 0, 0.3)';
      case 'image':
        return changeType === 'added' ? 'rgba(33, 150, 243, 0.3)' : 
               changeType === 'deleted' ? 'rgba(244, 67, 54, 0.3)' : 
               'rgba(33, 150, 243, 0.3)';
      case 'font':
        return changeType === 'added' ? 'rgba(156, 39, 176, 0.3)' : 
               changeType === 'deleted' ? 'rgba(244, 67, 54, 0.3)' : 
               'rgba(156, 39, 176, 0.3)';
      case 'style':
        return changeType === 'added' ? 'rgba(255, 152, 0, 0.3)' : 
               changeType === 'deleted' ? 'rgba(244, 67, 54, 0.3)' : 
               'rgba(255, 152, 0, 0.3)';
      default:
        return 'rgba(128, 128, 128, 0.3)';
    }
  };
  
  // Handle click on highlight layer to select a difference
  const handleHighlightClick = (e) => {
    if (!interactive || !differences.length || !onDifferenceSelect) return;
    
    // Get click coordinates relative to canvas
    const canvas = highlightLayerRef.current;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Scale coordinates to account for any CSS scaling
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const scaledX = x * scaleX;
    const scaledY = y * scaleY;
    
    // Find the difference that was clicked
    for (const diff of differences) {
      if (diff.bounds) {
        const { x: diffX, y: diffY, width, height } = diff.bounds;
        
        if (
          scaledX >= diffX && 
          scaledX <= diffX + width && 
          scaledY >= diffY && 
          scaledY <= diffY + height
        ) {
          onDifferenceSelect(diff);
          return;
        }
      }
    }
  };
  
  // Handle mouse movement over highlight layer
  const handleMouseMove = (e) => {
    if (!interactive || !differences.length) return;
    
    // Get mouse coordinates relative to canvas
    const canvas = highlightLayerRef.current;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Scale coordinates to account for any CSS scaling
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const scaledX = x * scaleX;
    const scaledY = y * scaleY;
    
    // Check if mouse is over any difference
    let foundDiff = null;
    for (const diff of differences) {
      if (diff.bounds) {
        const { x: diffX, y: diffY, width, height } = diff.bounds;
        
        if (
          scaledX >= diffX && 
          scaledX <= diffX + width && 
          scaledY >= diffY && 
          scaledY <= diffY + height
        ) {
          foundDiff = diff;
          break;
        }
      }
    }
    
    // Update hovered difference ID
    setHoveredDiffId(foundDiff ? foundDiff.id : null);
    
    // Update cursor style
    if (canvas) {
      canvas.style.cursor = foundDiff ? 'pointer' : 'default';
    }
  };
  
  // Handle mouse leaving the highlight layer
  const handleMouseLeave = () => {
    setHoveredDiffId(null);
  };

  return (
    <div className="pdf-renderer" style={{ opacity }}>
      {renderError && (
        <div className="render-error">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
          <span>{renderError}</span>
        </div>
      )}
      
      {loading && (
        <div className="renderer-loading">
          <div className="spinner"></div>
        </div>
      )}
      
      <div className="canvas-container" style={{ opacity: rendered ? 1 : 0.3 }}>
        {isImage ? (
          <img 
            ref={imageRef}
            className="pdf-image"
            alt="PDF page"
            style={{ 
              width: dimensions.width, 
              height: dimensions.height,
              display: rendered ? 'block' : 'none'
            }}
          />
        ) : (
          <canvas 
            ref={canvasRef} 
            className="pdf-canvas"
          />
        )}
        
        <canvas 
          ref={highlightLayerRef}
          className="highlight-layer"
          onClick={handleHighlightClick}
          onMouseMove={interactive ? handleMouseMove : undefined}
          onMouseLeave={interactive ? handleMouseLeave : undefined}
        />
        
        {interactive && (
          <div 
            ref={tooltipRef} 
            className="diff-tooltip"
            style={{ display: 'none' }}
          >
            {hoveredDiffId && 
              differences.find(d => d.id === hoveredDiffId)?.description}
          </div>
        )}
      </div>
      
      {/* Difference count badge */}
      {differences.length > 0 && (
        <div className="diff-count-badge">
          {differences.length}
        </div>
      )}
    </div>
  );
};

export default PDFRenderer;