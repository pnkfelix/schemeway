/*
 * Copyright (c) 2004 Nu Echo Inc.
 * 
 * This is free software. For terms and warranty disclaimer, see ./COPYING 
 */
package org.schemeway.plugins.schemescript.editor;

import org.eclipse.jface.action.*;
import org.eclipse.jface.preference.*;
import org.eclipse.jface.text.*;
import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.*;
import org.eclipse.jface.util.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.editors.text.*;
import org.eclipse.ui.texteditor.*;
import org.schemeway.plugins.schemescript.*;
import org.schemeway.plugins.schemescript.action.*;
import org.schemeway.plugins.schemescript.dictionary.*;
import org.schemeway.plugins.schemescript.indentation.*;
import org.schemeway.plugins.schemescript.parser.*;
import org.schemeway.plugins.schemescript.preferences.*;

public class SchemeEditor extends TextEditor {
    private SexpExplorer mExplorer;
    private PaintManager mPaintManager;
    private SchemeParenthesisPainter mParenPainter;

    public SchemeEditor() {
        super();
        SchemeTextTools textTools = SchemeScriptPlugin.getDefault().getTextTools();
        setSourceViewerConfiguration(new SchemeConfiguration(textTools));
        setDocumentProvider(new SchemeDocumentProvider());
        setPreferenceStore(SchemeScriptPlugin.getDefault().getPreferenceStore());
    }

    public void dispose() {
        super.dispose();
    }

    protected final boolean affectsTextPresentation(final PropertyChangeEvent event) {
        String property = event.getProperty();

        return property.startsWith(ColorPreferences.PREFIX) || property.startsWith(SyntaxPreferences.PREFIX);
    }

    protected void handlePreferenceStoreChanged(PropertyChangeEvent event) {
        String property = event.getProperty();
        if (property.equals(ColorPreferences.BACKGROUND_COLOR)) {
            getSourceViewer().getTextWidget().setBackground(new Color(null, (RGB) event.getNewValue()));
        }
        else if (property.equals(SchemePreferences.TAB_WIDTH)) {
            getSourceViewer().getTextWidget().setTabs(SchemePreferences.getTabWidth());
        }
        else
            if (affectsTextPresentation(event)) {
                SchemeTextTools textTools = SchemeScriptPlugin.getDefault().getTextTools();
                textTools.updateColors(event);

                IPreferenceStore store = getPreferenceStore();
                mParenPainter.setHighlightColor(new Color(null,
                                                          PreferenceConverter.getColor(store,
                                                                                       ColorPreferences.MATCHER_COLOR)));
                mParenPainter.setHighlightStyle(store.getBoolean(ColorPreferences.MATCHER_BOX));
            }
            else
                if (property.startsWith(IndentationPreferences.PREFIX)) {
                    SchemeTextTools textTools = SchemeScriptPlugin.getDefault().getTextTools();
                    textTools.updateIndentationSchemes();
                }
        super.handlePreferenceStoreChanged(event);
    }

    public final void createPartControl(final Composite parent) {
        super.createPartControl(parent);
        mPaintManager = new PaintManager(getSourceViewer());
        IPreferenceStore store = getPreferenceStore();
        Color color = new Color(null, PreferenceConverter.getColor(store, ColorPreferences.BACKGROUND_COLOR));
        getSourceViewer().getTextWidget().setBackground(color);
        startParenthesisHighlighting();
    }
    
    public ISymbolDictionary getSymbolDictionary() {
        return null;
    }

    protected void initializeKeyBindingScopes() {
        setKeyBindingScopes(new String[] {
            "Scheme Editing", "Edit"
        });
    }

    protected void createActions() {
        super.createActions();

        IAction action = new ForwardSExpAction(this, false);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_FORWARD);
        this.setAction(SchemeActionConstants.SEXP_FORWARD, action);

        action = new ForwardSExpAction(this, true);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_SELECT_FORWARD);
        this.setAction(SchemeActionConstants.SEXP_SELECT_FORWARD, action);

        action = new BackwardSExpAction(this, false);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_BACKWARD);
        this.setAction(SchemeActionConstants.SEXP_BACKWARD, action);

        action = new BackwardSExpAction(this, true);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_SELECT_BACKWARD);
        this.setAction(SchemeActionConstants.SEXP_SELECT_BACKWARD, action);

        action = new UpSExpAction(this, false);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_UP);
        this.setAction(SchemeActionConstants.SEXP_UP, action);

        action = new UpSExpAction(this, true);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_SELECT_UP);
        this.setAction(SchemeActionConstants.SEXP_SELECT_UP, action);

        action = new DownSExpAction(this);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_DOWN);
        this.setAction(SchemeActionConstants.SEXP_DOWN, action);

        action = new SwapSexpAction(this);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_SWAP);
        this.setAction(SchemeActionConstants.SEXP_SWAP, action);

        action = new FormatAction(this);
        action.setActionDefinitionId(SchemeActionConstants.SEXP_FORMAT);
        this.setAction(SchemeActionConstants.SEXP_FORMAT, action);

        action = SectioningCommentAction.createChapterCommentAction(this);
        action.setActionDefinitionId(SchemeActionConstants.COMMENT_CHAPTER);
        this.setAction(SchemeActionConstants.COMMENT_CHAPTER, action);

        action = SectioningCommentAction.createSectionCommentAction(this);
        action.setActionDefinitionId(SchemeActionConstants.COMMENT_SECTION);
        this.setAction(SchemeActionConstants.COMMENT_SECTION, action);

        action = new CommentAction(this);
        action.setActionDefinitionId(SchemeActionConstants.COMMENT_SELECTION);
        this.setAction(SchemeActionConstants.COMMENT_SELECTION, action);

        action = new FileHeaderCommentAction(this);
        action.setActionDefinitionId(SchemeActionConstants.COMMENT_HEADER);
        this.setAction(SchemeActionConstants.COMMENT_HEADER, action);

        action = new EvalExpressionAction(this, false);
        action.setActionDefinitionId(SchemeActionConstants.EVAL_EXPR);
        this.setAction(SchemeActionConstants.EVAL_EXPR, action);
        
        action = new EvalExpressionAction(this, true);
        action.setActionDefinitionId(SchemeActionConstants.EVAL_DEF);
        this.setAction(SchemeActionConstants.EVAL_DEF, action);
        
        action = new CompressSpacesAction(this);
        action.setActionDefinitionId(SchemeActionConstants.COMPRESS_SPACES);
        this.setAction(SchemeActionConstants.COMPRESS_SPACES, action);

        MouseCopyAction mouseAction = new MouseCopyAction(this, getSourceViewer().getTextWidget());
        mouseAction.setActionDefinitionId(SchemeActionConstants.SEXP_MOUSECOPY);
        this.setAction(SchemeActionConstants.SEXP_MOUSECOPY, mouseAction);
        
        action = new JumpToDefinitionAction(this);
        action.setActionDefinitionId(SchemeActionConstants.JUMP_DEF);
        this.setAction(SchemeActionConstants.JUMP_DEF, action);

        action= new TextOperationAction(SchemeScriptPlugin.getDefault().getResourceBundle(), "ContentAssistProposal.", this, ISourceViewer.CONTENTASSIST_PROPOSALS); //$NON-NLS-1$
        action.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
        setAction("ContentAssistProposal", action); 
            
        action = new TextOperationAction(SchemeScriptPlugin.getDefault().getResourceBundle(), "ContentAssistTip.", this, ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION);  //$NON-NLS-1$
        action.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_CONTEXT_INFORMATION);
        setAction("ContentAssistTip", action); 
    }

    private void startParenthesisHighlighting() {
        if (mParenPainter == null) {
            ISourceViewer sourceViewer = getSourceViewer();
            IPreferenceStore store = getPreferenceStore();
            mParenPainter = new SchemeParenthesisPainter(sourceViewer);
            mParenPainter.setHighlightStyle(store.getBoolean(ColorPreferences.MATCHER_BOX));
            mParenPainter.setHighlightColor(new Color(null,
                                                      PreferenceConverter.getColor(store,
                                                                                   ColorPreferences.MATCHER_COLOR)));
            mPaintManager.addPainter(mParenPainter);
        }

    }

    /** --- Helpers --- * */

    public SexpExplorer getExplorer() {
        if (mExplorer == null) {
            mExplorer = new SexpExplorer(getDocument());
        }
        return mExplorer;
    }

    public SchemeIndentationManager getIndentationManager() {
        return ((SchemeConfiguration) getSourceViewerConfiguration()).getTextTools().getIndentationManager();
    }

    public IDocument getDocument() {
        return this.getDocumentProvider().getDocument(this.getEditorInput());
    }

    public final Region getSelection() {
        Point selection = this.getSourceViewer().getSelectedRange();
        Region r = new Region(selection.x, selection.y);
        return r;
    }

    public int getPoint() {
        return this.getSourceViewer().getTextWidget().getCaretOffset();
    }

    public void setPoint(int offset) {
        this.getSourceViewer().setSelectedRange(offset, 0);
        getSourceViewer().revealRange(offset, 0);
    }

    public int getOffset(int x, int y) {
        return getSourceViewer().getTextWidget().getOffsetAtLocation(new Point(x, y));
    }

    public void setSelection(int start, int end) {
        Assert.isTrue(start <= end);
        getSourceViewer().setSelectedRange(start, end - start);
        getSourceViewer().revealRange(start, end - start);
    }

    public int getColumn(int offset) {
        try {
            return offset - getDocument().getLineOffset(offset);
        }
        catch (BadLocationException exception) {
            return 0;
        }
    }
    
    public void insertText(int offset, String text) {
        replaceText(offset, 0, text);
    }

    public void replaceText(int offset, int length, String text) {
        try {
            getDocument().replace(offset, length, text);
        }
        catch (BadLocationException exception) {
        }
    }

    public void swapText(int offset1, int length1, int offset2, int length2) {
        startCompoundChange();
        try {
            String text1 = getDocument().get(offset1, length1);
            String text2 = getDocument().get(offset2, length2);
            getDocument().replace(offset2, length2, text1);
            getDocument().replace(offset1, length1, text2);
        }
        catch (BadLocationException exception) {
        }
        endCompoundChange();
    }

    private void startCompoundChange() {
        ITextViewerExtension textViewer = (ITextViewerExtension) getSourceViewer();
        textViewer.setRedraw(false);
        getSourceViewerConfiguration().getUndoManager(getSourceViewer()).beginCompoundChange();
    }

    private void endCompoundChange() {
        ITextViewerExtension textViewer = (ITextViewerExtension) getSourceViewer();
        textViewer.setRedraw(true);
        getSourceViewerConfiguration().getUndoManager(getSourceViewer()).endCompoundChange();
    }

    public void runCompoundChange(Runnable runnable) {
        try {
            startCompoundChange();
            runnable.run();
        }
        finally {
            endCompoundChange();
        }
    }

    public final String getText(int offset, int length) {
        try {
            return getDocument().get(offset, length);
        }
        catch (BadLocationException exception) {
            return "";
        }
    }

    public final char getChar(int offset) {
        try {
            return getDocument().getChar(offset);
        }
        catch (BadLocationException exception) {
            return 0;
        }
    }

    /**
     * Returns the string that precedes offset on offset's line.
     */
    public final String getIdentation(int offset) {
        int column = getColumn(offset);
        return getText(offset - column, column);
    }
}