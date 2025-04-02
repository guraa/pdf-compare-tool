import React, { useEffect, useRef } from 'react';
import './DifferenceViewer.css'; // Assuming you have a CSS file for this

const DifferenceViewer = ({ basePdfUrl, comparePdfUrl, differences, pageNumber }) => {
    const baseCanvasRef = useRef(null);
    const compareCanvasRef = useRef(null);

    useEffect(() => {
        const drawDifferences = (canvas, pdfUrl, diffs) => {
            const context = canvas.getContext('2d');
            // Load PDF and render the page (using pdf.js) - Placeholder
            // This part requires integration with a PDF rendering library (like pdf.js)
            // and is simplified for brevity.

            // Example:
            // pdfjsLib.getDocument(pdfUrl).promise.then(pdf => {
            //   return pdf.getPage(pageNumber);
            // }).then(page => {
            //   const viewport = page.getViewport({ scale: 1 });
            //   canvas.height = viewport.height;
            //   canvas.width = viewport.width;
            //
            //   const renderContext = {
            //     canvasContext: context,
            //     viewport: viewport
            //   };
            //   page.render(renderContext);

            // Drawing differences
            if (diffs && diffs.length > 0) {
                diffs.forEach(diff => {
                    // Different color for different difference types
                    switch (diff.type) {
                        case 'text':
                            context.strokeStyle = 'red';
                            break;
                        case 'image':
                            context.strokeStyle = 'blue';
                            break;
                        case 'style':
                            context.strokeStyle = 'green';
                            break;
                        default:
                            context.strokeStyle = 'black';
                    }

                    context.lineWidth = 2;
                    context.strokeRect(diff.x, diff.y, diff.width, diff.height);

                    // Add tooltip data to the difference rectangle (simplified)
                    canvas.addEventListener('mousemove', (e) => {
                        const x = e.offsetX;
                        const y = e.offsetY;
                        if (x > diff.x && x < diff.x + diff.width && y > diff.y && y < diff.y + diff.height) {
                            // Show tooltip - this is a placeholder, you'll need a proper tooltip implementation
                            console.log(diff.details);
                        }
                    });
                });
            }
            // });
        };

        if (baseCanvasRef.current && compareCanvasRef.current) {
            // Placeholder: Replace with actual difference data fetching based on pageNumber
            const baseDifferences = differences ? differences.baseDifferences : [];
            const compareDifferences = differences ? differences.compareDifferences : [];

            drawDifferences(baseCanvasRef.current, basePdfUrl, baseDifferences);
            drawDifferences(compareCanvasRef.current, comparePdfUrl, compareDifferences);
        }
    }, [basePdfUrl, comparePdfUrl, differences, pageNumber]);

    return (
        <div className="difference-viewer">
            <canvas ref={baseCanvasRef} className="pdf-canvas base-pdf"/>
            <canvas ref={compareCanvasRef} className="pdf-canvas compare-pdf"/>
        </div>
    );
};

export default DifferenceViewer;