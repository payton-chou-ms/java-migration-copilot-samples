---
name: research-webpage
description: 'Extract and analyze URLs from webpages and images, fetch all linked content, organize findings by category with notes and diagrams, create summary overviews, and generate complete presentation structures with detailed content for each section.'
---

# Web Research and Report Generation

This skill provides a comprehensive workflow for conducting web research and generating structured reports from URLs and images.

## Workflow

### 1. URL Extraction
- **With Image**: When user provides a main URL and an image, extract all related URLs, titles, links, and categories from both the main content and the image
- **Without Image**: When only a main URL is provided, discover and extract all related URLs from the main link

### 2. Content Analysis
- Fetch and read the content from all discovered links
- Organize content by categories
- Create key notes, summaries, and highlights for each link
- Generate mind maps showing relationships between topics
- Create architecture diagrams to visualize information structure

### 3. Summary Generation
Create a comprehensive main page summary that includes:
- Overview of all content
- Key themes and topics identified
- Category breakdown
- Important insights and takeaways

### 4. Presentation Structure
Generate a complete PowerPoint (PPTX) report structure with approximately 15 pages:
- Title slide with research overview
- Table of contents
- Category-based sections (one section per major category)
- Key findings and insights for each section
- Detailed text content for each slide
- Visual elements suggestions (diagrams, mind maps, charts)
- Conclusion and recommendations
- References and sources

Each page should include:
- Slide title
- Main content text (bullet points and paragraphs)
- Suggested visuals or diagrams
- Speaker notes or additional context

## Tools to Use
- `fetch_webpage` - To retrieve content from URLs
- `semantic_search` - To find related content patterns
- `grep_search` - To extract specific information from fetched content
- Visual analysis tools - To process images and extract URLs

## Output Format
Deliver results in the following order:
1. **URL Extraction Report** - List of all discovered URLs with titles and categories
2. **Category-Organized Notes** - Detailed notes grouped by topic/category
3. **Mind Map** - Mermaid diagram showing topic relationships
4. **Architecture Diagram** - Mermaid diagram showing information structure
5. **Main Summary** - Comprehensive overview of all findings
6. **Presentation Structure** - Complete 15-page outline with full content for each slide
