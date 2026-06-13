# SillyTavern Memory Enhancement Integration

This directory contains a bundled SillyTavern memory enhancement extension used by the JiuGuanSJ integration workflow.

## Purpose

The extension is intended to help SillyTavern manage structured long-term memory for roleplay chats. In the JiuGuanSJ stack, the backend can generate conversation-level memory entries and synchronize them to SillyTavern worldbook/lorebook-style data.

## How It Fits Into JiuGuanSJ

Typical flow:

1. H5 users chat with a character through the JiuGuanSJ backend.
2. The backend stores messages and periodically extracts stable long-term memory.
3. Memory entries are saved in the backend database.
4. When enabled, memory entries are synchronized to SillyTavern as worldbook/lorebook content.
5. Future generation can use those entries as additional context.

## Installation

Install this extension into your SillyTavern extension directory according to your SillyTavern setup.

The exact path depends on how you run SillyTavern. Common locations include user extension folders under the SillyTavern `data/` directory.

After installation:

- Restart or reload SillyTavern.
- Confirm the extension is visible in SillyTavern's extension UI.
- Confirm the backend `SILLYTAVERN_BASE_URL` can reach your SillyTavern instance.
- Configure `SILLYTAVERN_API_KEY` if your SillyTavern setup requires API authentication.

## Notes

- This integration is optional. The backend can still store conversation memory even if SillyTavern worldbook synchronization is disabled or unavailable.
- Review the upstream SillyTavern and extension licenses before redistributing this directory.
- Do not commit private SillyTavern user data, chats, presets, API keys, or local runtime files.
