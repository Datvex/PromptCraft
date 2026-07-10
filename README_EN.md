<div align="center">

# PromptCraft

**AI building assistant for Minecraft (Fabric)**

🌐 Language: [Русский](README_RU.md) | **English**

</div>

---

## About the mod

PromptCraft is a Minecraft Java Edition mod built on the Fabric loader that lets you erect structures in the world from plain text descriptions. The player writes a request in natural language, the mod forwards it to the selected AI model (Anthropic, OpenAI, Google Gemini, DeepSeek, xAI, OpenRouter, or NVIDIA), receives back a precise sequence of build operations, and reproduces them in the game block by block, correctly handling stair orientation, doors, glass panes, and other engine-specific details of Minecraft.

The mod is installed as a regular `.jar` file placed in the `mods` folder (Fabric Loader + Fabric API, Minecraft 1.20.1, Java 17+). Building is only available while the player is in Creative mode.

---

## First launch

On startup the mod creates a `config/promptcraft/` folder with the following files:

- **config.json** — all mod settings (provider, model, modes, limits, visuals). Manual editing isn't required: almost everything is available and saved through the in-game menu.
- **.env** — provider API keys, stored separately from the general settings.

## Getting an API key

A key can be added directly from the game, without editing any files: the `/pmenu` command (or the K key) opens the menu, and the API tab lets you pick a provider and paste the key into the corresponding field — it is saved to `.env` immediately.

Keys are issued on the providers' own websites: Anthropic (console.anthropic.com), OpenAI (platform.openai.com), Google Gemini (aistudio.google.com), DeepSeek (platform.deepseek.com), xAI (console.x.ai), OpenRouter (openrouter.ai), NVIDIA NIM (build.nvidia.com).

---

## Basic workflow

1. Take the Selection Brush from the "PromptCraft" item group.
2. Left-click a block to set the first point of the build area, right-click a block to set the second point; a translucent outline appears between them.
3. Open the menu with the `/pmenu` command or the K key.
4. On the Create tab, type a text description of the build and press Generate.
5. The mod clears the selected area, sends the request to the AI, and once a response arrives begins building, with progress shown in the HUD.

The Edit button lets you refine the last request with a new phrase ("add windows", "make the roof taller") without reselecting the area — the AI reworks the result taking the edit into account. Undo (Clear) either interrupts the current generation or removes an already built structure entirely. The "Back" and "Next" buttons move through the build history, undoing and redoing individual steps.

While the ghost preview is active (see the free-area mode below), regular block breaking with the left mouse button is blocked — the click is used only to confirm placement.

---

## The `/pmenu` menu

The menu is opened with the `/pmenu` command or the K key and consists of seven tabs.

**Create** — the prompt field and the Generate, Edit, Undo (Clear), "Back"/"Next" buttons, plus the build-area mode switch (Manual Area / AI Free Area, described below).

**API** — provider selection from a list with icons, an API key field for the current provider, model selection (manually or by refreshing the model list from the provider's servers), and the creativity switch (High Creativity / Strict Prompt, covered in detail in its own section below).

**Animations** — a toggle for displaying text messages about the process (preparing the area, contacting the AI, building).

**Language** — switches the mod's interface between English and Russian. On first joining a world, the language is picked automatically based on the Minecraft client's language.

**Theme** — a full color picker (saturation/value field plus a hue strip) for the mod interface's accent color, manual HEX input, and a reset-to-default button.

**Visual** — settings for how the selection area is displayed: outline thickness, whether the outline is visible through blocks (an x-ray-like effect), and the opacity of the selected area's fill.

**Limits** — enabling a hard cap on build dimensions, plus the maximum width, height, and depth values in blocks. These limits also apply in free mode, setting a safety ceiling for what the AI can build without an explicit selection.

---

## Build-area modes: Manual Area and AI Free Area

The switch is located on the Create tab.

In **Manual Area** mode, the build area is defined by the player using the Selection Brush. The bounds are strict: the AI must fit exactly within the given dimensions, and if it tries to go beyond them, the engine asks the model again with a correction, repeating the request up to a limited number of times.

In **AI Free Area** mode, there's no need to select anything: pressing Generate sends the request immediately, and the AI decides on a suitable size for the structure based on the description, staying within a safe technical ceiling or the limits from the Limits tab if they're enabled. Once a response arrives, a translucent ghost preview of the build appears in the world; it can be moved along with the cursor and rotated with the R key. Confirming placement with the left mouse button opens a dialog with a question and "Yes"/"No" buttons; only after confirmation is the area actually cleared and the structure placed in the world.

---

## Creativity switch: High Creativity and Strict Prompt

Located on the API tab, this switch determines not only the artistic style of the result but also the parameters of the request sent to the model itself.

**High Creativity** casts the model as a high-level master builder. The system instruction explicitly forbids flat, single-material boxes and requires: mixing a block palette (a main, secondary, accent, and textured variants — for example brick, cobblestone, andesite, plus cracked and mossy versions), adding relief to walls through offsets and insets, building layered pitched roofs with spires instead of flat ceilings, shaping an expressive silhouette with battlements and overhangs, adding lighting, windows, an entrance area, and small decorative details, and neatly grounding the build in its surroundings with a small base of grass or paths. The model is given a detailed reference example of turning a simple request like "a stone tower" into a multi-tiered, detailed structure. The generation temperature in this mode is 0.85, giving the model more variability and creative freedom.

**Strict Prompt** puts the model into the role of a precise, literal executor. Its only task is to build exactly what the request describes: no added decoration, landscaping, or artistic liberties unless explicitly mentioned. Materials, dimensions, and shape are followed exactly as the player specified; if some detail isn't mentioned, the simplest neutral implementation is chosen instead of the model inventing something on its own. The generation temperature is 0.2, making the model's behavior as predictable and deterministic as possible.

Switching between modes takes effect instantly, is saved in the configuration, and applies to the very next request without restarting the game. High Creativity suits artistic builds and "surprise me" requests, while Strict Prompt suits precise technical specifications, blueprint copies, and redstone-heavy builds where literal accuracy matters.

---

## Build operation format

The AI doesn't return an image — it sends back JSON with a sequence of operations that the mod executes in order. The place operation sets a single block at a given point with optional states (orientation, door half, and so on). The fill operation fills a solid rectangular volume with one block. The hollow_box operation creates a hollow box — walls, floor, and ceiling one block thick. Later operations overwrite earlier ones at the same coordinates, which lets the model first raise a solid shell and then carve windows and doors into it with an air block, adding details on top afterward.

The mod additionally validates that all coordinates stay within the selected or safe area, automatically connects glass panes, iron bars, fences, walls, and redstone wire without requiring explicit connection states, recovers operations from a truncated or malformed model response (for example, if the streamed response is cut off), and, when needed, asks the AI again with a correction — the number of such attempts is limited by a setting in the configuration.

---

## AI providers

The mod communicates through a unified interface with seven providers, each with its own request quirks accounted for. Anthropic uses streaming with a separate system prompt and automatic selection of the token limit for the model. OpenAI supports reasoning models with a "developer" role and a higher reasoning effort. Google Gemini uses its own request schema with separate system instructions and content, along with an enabled mode for showing the model's chain of thought. DeepSeek uses an OpenAI-compatible request format. xAI and OpenRouter also request a higher reasoning effort before responding. NVIDIA NIM is the default provider and, for supported models, enables a thinking-display mode. For every provider, the API tab has a refresh button that fetches the current model list directly from the provider's servers.

---

## Controls

The K key opens and closes the PromptCraft menu. The R key rotates the ghost preview by ninety degrees in free build mode. The left mouse button confirms the current position of the ghost and opens the placement confirmation dialog. All keys can be rebound in Minecraft's standard controls menu, under the "PromptCraft" category.
