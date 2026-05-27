# Google FlexAgent SDK Skills

This folder contains skills for the Google FlexAgent SDK. Skills are reusable
components that provide specialized capabilities, tools, and knowledge to
agents.

## Installation

You can browse and install skills using the skills CLI.

### Using Vercel skills CLI

```bash
# Interactively browse and install skills.
npx skills add Google-FlexAgent/flexagent-sdk-python --list

# Install a specific skill (e.g., google-flexagent-sdk).
npx skills add Google-FlexAgent/flexagent-sdk-python --skill google-flexagent-sdk --global
```

### Using Context7 skills CLI

```bash
# Interactively browse and install skills.
npx ctx7 skills install /Google-FlexAgent/flexagent-sdk-python

# Install a specific skill (e.g., google-flexagent-sdk).
npx ctx7 skills install /Google-FlexAgent/flexagent-sdk-python google-flexagent-sdk
```

## Available Skills

### Google FlexAgent SDK (`google-flexagent-sdk`)

Provides core documentation and examples for building AI agents with the
FlexAgent SDK. It includes guides on:

-   Agent configuration
-   Error handling
-   Hooks
-   MCP integration
-   And more.
