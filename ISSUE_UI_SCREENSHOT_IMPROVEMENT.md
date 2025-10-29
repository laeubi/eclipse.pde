# Improve UI screenshot generation for PR documentation

## Problem

The current approach to generating screenshots for UI components using Python/PIL produces white/blank images that don't accurately represent the Eclipse UI.

## Proposed Solutions

### 1. SWT Snippet Approach
Create small SWT snippets that can render the UI components. However, this requires complex setup for many components that depend on Eclipse platform infrastructure.

### 2. MCP Server with Eclipse Install (Recommended)
Create a Model Context Protocol (MCP) server that includes a full Eclipse installation. This would allow:
- Programmatic access to Eclipse UI components
- Automated screenshot capture of actual rendered UI
- Better representation of Eclipse styling and themes
- Reusable infrastructure for future UI documentation

## Context

This issue was identified while implementing the Source Lookups preference page (see related PR for #2073). The generated screenshot using Python/PIL doesn't accurately show how the preference page would look in a real Eclipse environment.

## Benefits of MCP Server Approach

- **Accuracy**: Screenshots show actual Eclipse UI rendering with proper fonts, colors, and styling
- **Reusability**: Can be used for all future UI-related PRs and documentation
- **Automation**: Can be integrated into CI/CD pipeline for automatic screenshot generation
- **Quality**: Better documentation quality improves contributor and user experience

## Implementation Ideas

1. Create a headless Eclipse installation in a container
2. Build an MCP server that can:
   - Launch Eclipse workbench programmatically
   - Navigate to specific UI components (dialogs, preference pages, views, etc.)
   - Capture screenshots using SWT/platform APIs
   - Return screenshots as base64 or file paths
3. Integrate with Copilot workflow for automatic screenshot generation

## Labels

- `enhancement`
- `documentation`
- `tooling`

## Related

- Source Lookups preference page PR
- Issue #2073
