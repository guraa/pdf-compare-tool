import React, { useState, useEffect, useRef, useCallback } from "react";
import Spinner from "../common/Spinner";
import "./PDFRenderer.css";

/**
 * Enhanced PDFRenderer component with improved coordinate handling
 * for precise difference highlighting
 */
const PDFRenderer = ({
  fileId,
  page,
  zoom = 0.75,
  highlightMode = "all",
  differences = [],
  selectedDifference = null,
  onDifferenceSelect,
  onZoomChange,
  isBaseDocument = false,
  loading = false,
  opacity = 1,
  flipY = true,
  pageMetadata = null,
  // Fine-tuning adjustment parameters
  xOffsetAdjustment = 0,
  yOffsetAdjustment = 0,
  scaleAdjustment = 1,
}) => {
  // State
  const [renderError, setRenderError] = useState(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [imageLoaded, setImageLoaded] = useState(false);
  const [scaleFactor, setScaleFactor] = useState(1);
  const [containerOffset, setContainerOffset] = useState({ x: -20, y: -20 });
  const [adjustedCoordinates, setAdjustedCoordinates] = useState([]);

  // Store actual coordinates for debugging
  const [mouseCoords, setMouseCoords] = useState({ x: 0, y: 0 });

  // Refs
  const imageRef = useRef(null);
  const canvasRef = useRef(null);
  const containerRef = useRef(null);

  // URL for the image
  const imageUrl = `/api/pdfs/document/${fileId}/page/${page}`;

  // Log important information when differences change
  useEffect(() => {
    console.log(
      `PDFRenderer: rendering for fileId=${fileId}, page=${page}, zoom=${zoom}`
    );
    console.log("PDFRenderer: Differences count:", differences?.length || 0);

    // Debug first few differences to understand their structure
    if (differences && differences.length > 0) {
      console.log("PDFRenderer: Sample differences:", differences.slice(0, 2));
    }

    // Check if differences have valid coordinates
    const validDiffs = differences?.filter((diff) => {
      // Check for both formats (x/y and position.x/position.y)
      return (
        diff &&
        ((diff.x !== undefined &&
          diff.y !== undefined &&
          diff.width !== undefined &&
          diff.height !== undefined) ||
          (diff.position &&
            diff.position.x !== undefined &&
            diff.position.y !== undefined &&
            diff.bounds &&
            diff.bounds.width !== undefined &&
            diff.bounds.height !== undefined))
      );
    });

    console.log(
      `PDFRenderer: Valid differences with coordinates: ${
        validDiffs?.length || 0
      } of ${differences?.length || 0}`
    );
  }, [differences, fileId, page, zoom]);

  // Calculate scale factor once the image is loaded
  useEffect(() => {
    if (imageLoaded && imageRef.current && pageMetadata) {
      const image = imageRef.current;

      // Get the natural dimensions of the loaded image
      const imageNaturalWidth = image.naturalWidth;
      const imageNaturalHeight = image.naturalHeight;

      // Get the actual PDF dimensions from the metadata
      const pdfWidth = isBaseDocument
        ? pageMetadata.baseWidth
        : pageMetadata.compareWidth;
      const pdfHeight = isBaseDocument
        ? pageMetadata.baseHeight
        : pageMetadata.compareHeight;

      if (pdfWidth && pdfHeight) {
        // Calculate the scale factor between the rendered image and actual PDF
        const calculatedScaleFactor =
          (imageNaturalWidth / pdfWidth) * scaleAdjustment;

        console.log(
          `PDFRenderer: Calculated scale factor: ${calculatedScaleFactor}`
        );
        console.log(
          `PDFRenderer: Image natural size: ${imageNaturalWidth}x${imageNaturalHeight}`
        );
        console.log(`PDFRenderer: PDF dimensions: ${pdfWidth}x${pdfHeight}`);

        setScaleFactor(calculatedScaleFactor);

        // Measure the container to get offsets
        if (containerRef.current) {
          const containerRect = containerRef.current.getBoundingClientRect();
          const imageRect = image.getBoundingClientRect();

          // Calculate the actual offset of the image within the container
          const calculatedOffsetX = imageRect.left - containerRect.left;
          const calculatedOffsetY = imageRect.top - containerRect.top;

          // Apply fine-tuning adjustments
          const finalOffsetX = calculatedOffsetX + xOffsetAdjustment;
          const finalOffsetY = calculatedOffsetY + yOffsetAdjustment;


          setContainerOffset({ x: finalOffsetX, y: finalOffsetY });

          console.log(`PDFRenderer: Calculated offsets: (${calculatedOffsetX.toFixed(2)}, ${calculatedOffsetY.toFixed(2)})`);
          console.log(`PDFRenderer: Adjustments: (${xOffsetAdjustment}, ${yOffsetAdjustment})`);
          console.log(`PDFRenderer: Final Container offsets: (${finalOffsetX.toFixed(2)}, ${finalOffsetY.toFixed(2)})`);
        }
      } else {
        console.warn("PDFRenderer: Missing PDF dimensions in pageMetadata");
        // Default to a reasonable scale factor if we don't have PDF dimensions
        setScaleFactor(0.33 * scaleAdjustment);
      }
    }
  }, [
    imageLoaded,
    isBaseDocument,
    pageMetadata,
    scaleAdjustment,
    xOffsetAdjustment,
    yOffsetAdjustment,
  ]);

  // Pre-calculate and store adjusted coordinates when scale factor changes
  useEffect(() => {
    if (!differences || !scaleFactor) return;

    // Transform all difference coordinates
    const transformed = differences
      .map((diff) => {
        // Get coordinates based on the available format
        let x, y, width, height, id, type, changeType, text;

        if (diff.x !== undefined && diff.y !== undefined) {
          x = diff.x;
          y = diff.y;
          width = diff.width;
          height = diff.height;
        } else if (diff.position && diff.bounds) {
          x = diff.position.x;
          y = diff.position.y;
          width = diff.bounds.width;
          height = diff.bounds.height;
        } else {
          return null; // Skip if missing coordinate data
        }

        // Keep other important properties
        id = diff.id;
        type = diff.type;
        changeType = diff.changeType;
        text = diff.text || diff.baseText || diff.compareText;

        // Apply scaling and adjustments
        const displayX = x * scaleFactor * zoom + containerOffset.x;

        // Apply Y-flip if enabled
        let displayY;
        if (flipY && canvasRef.current) {
          // Flip Y-coordinate (bottom-left to top-left)
          displayY =
            canvasRef.current.height -
            y * scaleFactor * zoom -
            height * scaleFactor * zoom +
            containerOffset.y;
        } else {
          // Regular Y-coordinate (already in top-left origin)
          displayY = y * scaleFactor * zoom + containerOffset.y;
        }

        const displayWidth = width * scaleFactor * zoom;
        const displayHeight = height * scaleFactor * zoom;

        // Apply type-specific adjustments
        let adjustedX = displayX;
        let adjustedY = displayY;
        let adjustedWidth = displayWidth;
        let adjustedHeight = displayHeight;

        // Text elements might need special handling
        if (type === "text") {
          // Fine-tune text highlight position
          adjustedHeight = Math.max(displayHeight, 14 * zoom); // Ensure text highlights have minimum height

          // Adjust vertical position to better match text baseline
          adjustedY = displayY - 2 * zoom;
        }

        return {
          id,
          type,
          changeType,
          text,
          x: adjustedX,
          y: adjustedY,
          width: adjustedWidth,
          height: adjustedHeight,
          originalX: x,
          originalY: y,
          originalWidth: width,
          originalHeight: height,
        };
      })
      .filter(Boolean);

    setAdjustedCoordinates(transformed);

    // Log sample transformed coordinates for debugging
    if (transformed.length > 0) {
      console.log(
        "PDFRenderer: Sample transformed coordinates:",
        transformed[0]
      );
    }
  }, [differences, scaleFactor, zoom, containerOffset,flipY]);

  // Function to convert PDF coordinates to canvas/screen coordinates
  const pdfToCanvasCoords = useCallback(
    (x, y, width, height) => {
      if (!canvasRef.current) return { x: 0, y: 0, width: 0, height: 0 };

      const canvas = canvasRef.current;

      const canvasX = x * scaleFactor * zoom + containerOffset.x;

      let canvasY;
      if (flipY) {
        // Flip Y-coordinate (bottom-left to top-left)
        canvasY =
          canvas.height -
          y * scaleFactor * zoom -
          height * scaleFactor * zoom +
          containerOffset.y;
      } else {
        // Regular Y-coordinate (already in top-left origin)
        canvasY = y * scaleFactor * zoom + containerOffset.y;
      }

      const canvasWidth = width * scaleFactor * zoom;
      const canvasHeight = height * scaleFactor * zoom;

      return {
        x: canvasX,
        y: canvasY,
        width: canvasWidth,
        height: canvasHeight,
      };
    },
    [scaleFactor, zoom, containerOffset, flipY]
  );

  // Function to convert canvas/screen coordinates to PDF coordinates
  const canvasToPdfCoords = useCallback(
    (canvasX, canvasY) => {
      if (!canvasRef.current || scaleFactor === 0) return { x: 0, y: 0 };

      const canvas = canvasRef.current;

      const pdfX = (canvasX - containerOffset.x) / (scaleFactor * zoom);

      // Handle Y-flip if enabled
      let pdfY;
      if (flipY) {
        // Inverse of flipped Y-coordinate
        pdfY =
          (canvas.height - canvasY + containerOffset.y) / (scaleFactor * zoom);
      } else {
        // Regular Y-coordinate inverse
        pdfY = (canvasY - containerOffset.y) / (scaleFactor * zoom);
      }

      return {
        x: pdfX,
        y: pdfY,
      };
    },
    [scaleFactor, zoom, containerOffset, flipY]
  );

  // Handle image load
  const handleImageLoad = () => {
    console.log(`PDFRenderer: Image loaded successfully: page ${page}`);

    if (!imageRef.current) {
      console.error("PDFRenderer: imageRef is null after load!");
      return;
    }

    const image = imageRef.current;
    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;

    console.log(
      `PDFRenderer: Natural image dimensions: ${naturalWidth}x${naturalHeight}`
    );

    const scaledWidth = naturalWidth * zoom;
    const scaledHeight = naturalHeight * zoom;

    setDimensions({
      width: scaledWidth,
      height: scaledHeight,
      naturalWidth,
      naturalHeight,
    });

    setImageLoaded(true);
    setIsLoading(false);

    // Call zoom change callback if provided
    if (onZoomChange) {
      onZoomChange(zoom);
    }
  };

  // Handle image error
  const handleImageError = (error) => {
    console.error(`PDFRenderer: Error loading image for page ${page}:`, error);
    setRenderError(
      `Failed to load page ${page}. The server may be unavailable or the document format is not supported.`
    );
    setIsLoading(false);
  };

  // Draw highlights on canvas
  useEffect(() => {
    if (
      !canvasRef.current ||
      !imageLoaded ||
      highlightMode === "none" ||
      !adjustedCoordinates.length
    ) {
      return;
    }

    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");

    // Set canvas dimensions to match the image
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    console.log(
      `PDFRenderer: Canvas dimensions set to ${canvas.width}x${canvas.height}`
    );

    // Skip if no differences
    if (!adjustedCoordinates || adjustedCoordinates.length === 0) {
      console.log("PDFRenderer: No adjusted coordinates to highlight");
      return;
    }

    console.log(
      `PDFRenderer: Drawing ${adjustedCoordinates.length} differences on canvas`
    );

    // Draw differences on the canvas
    adjustedCoordinates.forEach((diff, index) => {
      // Skip if type doesn't match highlight mode (unless mode is 'all')
      if (highlightMode !== "all" && diff.type !== highlightMode) {
        return;
      }

      // Get the pre-calculated adjusted coordinates
      const { x, y, width, height, type } = diff;

      // Generate highlight color based on difference type
      let fillColor;
      let strokeColor;

      switch (type) {
        case "text":
          fillColor = "rgba(255, 82, 82, 0.3)"; // Red
          strokeColor = "rgba(255, 82, 82, 0.8)";
          break;
        case "image":
          fillColor = "rgba(33, 150, 243, 0.3)"; // Blue
          strokeColor = "rgba(33, 150, 243, 0.8)";
          break;
        case "font":
          fillColor = "rgba(156, 39, 176, 0.3)"; // Purple
          strokeColor = "rgba(156, 39, 176, 0.8)";
          break;
        case "style":
          fillColor = "rgba(255, 152, 0, 0.3)"; // Orange
          strokeColor = "rgba(255, 152, 0, 0.8)";
          break;
        default:
          fillColor = "rgba(0, 150, 136, 0.3)"; // Teal
          strokeColor = "rgba(0, 150, 136, 0.8)";
      }

      // Override colors based on change type if available
      if (diff.changeType === "added") {
        fillColor = "rgba(76, 175, 80, 0.3)"; // Green for added
        strokeColor = "rgba(76, 175, 80, 0.8)";
      } else if (diff.changeType === "deleted") {
        fillColor = "rgba(244, 67, 54, 0.3)"; // Red for deleted
        strokeColor = "rgba(244, 67, 54, 0.8)";
      }

      // Draw the highlight with vibrant color
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2;

      // For text differences, use rounded rectangle for better appearance
      if (type === "text") {
        // Draw rounded rectangle
        const radius = 4;
        ctx.beginPath();
        ctx.moveTo(x + radius, y);
        ctx.lineTo(x + width - radius, y);
        ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
        ctx.lineTo(x + width, y + height - radius);
        ctx.quadraticCurveTo(
          x + width,
          y + height,
          x + width - radius,
          y + height
        );
        ctx.lineTo(x + radius, y + height);
        ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
        ctx.lineTo(x, y + radius);
        ctx.quadraticCurveTo(x, y, x + radius, y);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
      } else {
        // For other differences, use standard rectangle
        ctx.fillRect(x, y, width, height);
        ctx.strokeRect(x, y, width, height);
      }

      // If this is the selected difference, add a more visible indicator
      if (selectedDifference && selectedDifference.id === diff.id) {
        ctx.strokeStyle = "rgba(255, 255, 0, 0.9)";
        ctx.lineWidth = 3;
        ctx.strokeRect(x - 3, y - 3, width + 6, height + 6);

        // Add a label for the selected difference if it has text
        if (diff.text && width > 50) {
          ctx.fillStyle = "rgba(0, 0, 0, 0.7)";
          ctx.font = `${Math.max(10, Math.min(14, width / 10))}px Arial`;
          const textToShow =
            diff.text.substring(0, 20) + (diff.text.length > 20 ? "..." : "");
          ctx.fillText(textToShow, x + 5, y - 5);
        }
      }
    });
  }, [
    adjustedCoordinates,
    highlightMode,
    dimensions,
    selectedDifference,
    imageLoaded,
  ]);

  // Handle canvas mouseover for better debugging
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const handleMouseMove = (e) => {
      const canvasRect = canvas.getBoundingClientRect();

      // Get mouse position relative to canvas in screen coordinates
      const mouseX =
        (e.clientX - canvasRect.left) * (canvas.width / canvasRect.width);
      const mouseY =
        (e.clientY - canvasRect.top) * (canvas.height / canvasRect.height);

      // Store for debug overlay
      setMouseCoords({ x: mouseX, y: mouseY });

      // Convert to PDF coordinates
      const pdfCoords = canvasToPdfCoords(mouseX, mouseY);

      // Debug in developer console occasionally to avoid spam
      if (Math.random() < 0.01) {
        // Only log 1% of movements
        console.log(
          `Mouse - Canvas: (${mouseX.toFixed(2)}, ${mouseY.toFixed(
            2
          )}), PDF: (${pdfCoords.x.toFixed(2)}, ${pdfCoords.y.toFixed(2)})`
        );
      }
    };

    canvas.addEventListener("mousemove", handleMouseMove);

    return () => {
      canvas.removeEventListener("mousemove", handleMouseMove);
    };
  }, [canvasToPdfCoords]);

  // Handle click on canvas to select a difference
  const handleCanvasClick = (e) => {
    if (
      !canvasRef.current ||
      !onDifferenceSelect ||
      !adjustedCoordinates?.length
    )
      return;

    // Get mouse coordinates
    const canvas = canvasRef.current;
    const canvasRect = canvas.getBoundingClientRect();

    // Get click position in canvas coordinates
    const canvasX =
      (e.clientX - canvasRect.left) * (canvas.width / canvasRect.width);
    const canvasY =
      (e.clientY - canvasRect.top) * (canvas.height / canvasRect.height);

    // Find if we clicked on a difference
    let clickedDiff = null;

    for (const diff of adjustedCoordinates) {
      // Check if the click is inside this difference
      if (
        canvasX >= diff.x &&
        canvasX <= diff.x + diff.width &&
        canvasY >= diff.y &&
        canvasY <= diff.y + diff.height
      ) {
        // Find the original difference object to maintain all properties
        const originalDiff = differences.find((d) => d.id === diff.id);
        if (originalDiff) {
          clickedDiff = originalDiff;
          break;
        }
      }
    }

    if (clickedDiff) {
      console.log("PDFRenderer: Clicked on difference:", clickedDiff);
      onDifferenceSelect(clickedDiff);
    }
  };

  // Set up the image when fileId or page changes
  useEffect(() => {
    setIsLoading(true);
    setImageLoaded(false);
  }, [fileId, page]);

  // Update dimensions when zoom changes
  useEffect(() => {
    if (imageLoaded && imageRef.current) {
      const image = imageRef.current;
      setDimensions((prev) => ({
        ...prev,
        width: image.naturalWidth * zoom,
        height: image.naturalHeight * zoom,
      }));
    }
  }, [zoom, imageLoaded]);

  // Render error state
  if (renderError) {
    return (
      <div className="pdf-renderer-empty" style={{ opacity }}   ref={containerRef}>
        <div className="pdf-error-message">
          <div className="error-icon">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
            </svg>
          </div>
          <p>{renderError}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="pdf-renderer" style={{ opacity }} ref={containerRef}>
      {/* Debug overlay */}
      <div
        style={{
          position: "absolute",
          top: 5,
          left: 5,
          background: "rgba(0,0,0,0.7)",
          color: "white",
          padding: "4px 8px",
          fontSize: "10px",
          borderRadius: "4px",
          pointerEvents: "none",
          zIndex: 1000,
        }}
      >
        <div>
          Page: {page} | Zoom: {Math.round(zoom * 100)}%
        </div>
        <div>Scale: {scaleFactor.toFixed(3)}</div>
        <div>
          Offset: ({containerOffset.x}, {containerOffset.y})
        </div>
        <div>
          Mouse: ({mouseCoords.x.toFixed(0)}, {mouseCoords.y.toFixed(0)})
        </div>
        <div>Y-Flip: {flipY ? 'Enabled' : 'Disabled'}</div>
      </div>

      {/* Loading indicator */}
      {(isLoading || loading) && (
        <div className="renderer-loading">
          <Spinner size="medium" />
          <div className="loading-message">Loading page {page}...</div>
        </div>
      )}

      {/* Image and highlights container */}
      <div
        className="canvas-container"
        style={{
          opacity: imageLoaded ? 1 : 0.3,
        }}
      >
        <img
          ref={imageRef}
          className="pdf-image"
          src={imageUrl}
          alt={`PDF page ${page}`}
          style={{
            width: dimensions.width,
            height: dimensions.height,
          }}
          onLoad={handleImageLoad}
          onError={handleImageError}
        />

        <canvas
          ref={canvasRef}
          className="highlight-layer"
          onClick={handleCanvasClick}
          width={dimensions.width}
          height={dimensions.height}
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            pointerEvents: "auto",
            zIndex: 10,
          }}
        />
      </div>

      {/* Difference count badge */}
      {adjustedCoordinates.length > 0 && imageLoaded && (
        <div className="diff-count-badge">{adjustedCoordinates.length}</div>
      )}

      {/* Zoom controls */}
      {onZoomChange && (
        <div className="pdf-controls">
          <button
            title="Zoom Out"
            onClick={() => onZoomChange(Math.max(zoom - 0.25, 0.5))}
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7z" />
            </svg>
          </button>

          <div className="zoom-value" onClick={() => onZoomChange(1.0)}>
            {Math.round(zoom * 100)}%
          </div>

          <button
            title="Zoom In"
            onClick={() => onZoomChange(Math.min(zoom + 0.25, 3.0))}
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" />
              <path d="M12 10h-2v-2h-1v2h-2v1h2v2h1v-2h2z" />
            </svg>
          </button>

          <span className="page-indicator">{page}</span>
        </div>
      )}
    </div>
  );
};

export default PDFRenderer;
