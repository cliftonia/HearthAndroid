package app.hearth.chat

import app.hearth.domain.CanvasReference

// The way from a reply's card into the canvas.

/** Opens a piece of a reply in the canvas. */
fun ChatPresenter.canvasAction(reference: CanvasReference) = delegate?.chatPresenterDidRequestCanvas(reference) ?: Unit
