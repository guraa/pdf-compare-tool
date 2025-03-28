#!/bin/bash

# Script to generate favicon.ico and PNG files from favicon.svg
# Requires Inkscape and ImageMagick to be installed

# Generate PNG images of different sizes
echo "Generating PNG files from SVG..."
inkscape -w 16 -h 16 favicon.svg -o icon-16.png
inkscape -w 32 -h 32 favicon.svg -o icon-32.png
inkscape -w 48 -h 48 favicon.svg -o icon-48.png
inkscape -w 64 -h 64 favicon.svg -o icon-64.png
inkscape -w 192 -h 192 favicon.svg -o logo192.png
inkscape -w 512 -h 512 favicon.svg -o logo512.png

# Create favicon.ico with multiple sizes
echo "Creating favicon.ico..."
convert icon-16.png icon-32.png icon-48.png icon-64.png favicon.ico

# Clean up temporary files
echo "Cleaning up temporary files..."
rm icon-16.png icon-32.png icon-48.png icon-64.png

echo "Done! Icons generated successfully."