/*
 * Copyright (c) 2004 Nu Echo Inc.
 * 
 * This is free software. For terms and warranty disclaimer, see ./COPYING
 */
package org.schemeway.plugins.schemescript.indentation;

import org.eclipse.jface.preference.*;
import org.eclipse.jface.text.*;

import org.schemeway.plugins.schemescript.*;
import org.schemeway.plugins.schemescript.parser.*;
import org.schemeway.plugins.schemescript.preferences.*;

public class SchemeIndentationStrategy implements IAutoIndentStrategy {
    private SexpExplorer mExplorer;
    private SchemeIndentationManager mIndentationManager;

    public SchemeIndentationStrategy(SchemeIndentationManager indentManager) {
        Assert.isNotNull(indentManager);
        mIndentationManager = indentManager;
    }

    public void dispose() {
        mExplorer.dispose();
        mExplorer = null;
    }

    public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
        initializeStrategy(document);
        if (command.length == 0
            && command.text != null
            && TextUtilities.endsWith(document.getLegalLineDelimiters(), command.text) != -1) {
            try {
                IRegion lineInfo = document.getLineInformationOfOffset(command.offset);
                String line = document.get(lineInfo.getOffset(), lineInfo.getLength());
                String prefix = CommentPreferences.getCommentPrefix();
                IPreferenceStore store = SchemeScriptPlugin.getDefault().getPreferenceStore();
                boolean continueComment = store.getBoolean(CommentPreferences.COMMENT_CONTINUE);

                if (continueComment && line.startsWith(prefix) && (!line.equals(prefix + " "))) {
                    command.text = command.text + prefix + " ";
                }
                else {
                    autoIndentAfterNewLine(document, command);
                }
            }
            catch (BadLocationException exception) {

            }
        }
    }

    private void initializeStrategy(IDocument document) {
        if (mExplorer == null || document != mExplorer.getDocument()) {
            if (mExplorer != null)
                mExplorer.dispose();
            mExplorer = new SexpExplorer(document);
        }
    }

    private void autoIndentAfterNewLine(IDocument document, DocumentCommand command) {
        try {
            int indentation = findIndentation(new SchemeIndentationContext(mExplorer,
                                                                           mIndentationManager,
                                                                           command.offset));
            if (indentation > 0) {
                StringBuffer buffer = new StringBuffer(command.text);

                for (int i = 0; i < indentation; i++)
                    buffer.append(' ');
                command.text = buffer.toString();
            }
            removeLeadingSpaces(document, command);
        }
        catch (BadLocationException exception) {
        }
    }

    public static int findIndentation(SchemeIndentationContext context) throws BadLocationException {
        int indentation = 0;
        SexpExplorer explorer = context.getExplorer();
        IDocument document = explorer.getDocument();

        if (explorer.backwardSexpression(context.getOffset())) {
            // There is an S-expression before the insertion point
            int previousStart = context.getExplorer().getSexpStart();

            if (explorer.upSexpression(previousStart)) {
                // We are inside an S-expression
                int outerStart = explorer.getSexpStart();

                explorer.downSexpression(outerStart);
                explorer.forwardSexpression(explorer.getSexpStart());
                // we have a form '(symbol ...)'
                if (explorer.getSexpType() == SexpExplorer.TYPE_SYMBOL) {
                    // Find the indentation scheme
                    String text = explorer.getText();
                    IndentationScheme scheme = context.getManager().getFunction(text);

                    indentation = findIndentationFromScheme(context.getExplorer(),
                                                            context.getOffset(),
                                                            previousStart,
                                                            outerStart,
                                                            scheme);
                }
                else
                    if (explorer.getSexpType() == SexpExplorer.TYPE_LIST) {
                        indentation = findColumn(document, explorer.getSexpStart());
                    }
                    else
                        indentation = findColumn(document, previousStart);
            }
            else {
                indentation = findColumn(document, previousStart);
            }
        }
        else
            if (explorer.upSexpression(context.getOffset())) {
                indentation = findColumn(document, explorer.getSexpStart()) + 1;
            }
        return indentation;
    }

    private static int findIndentationFromScheme(SexpExplorer explorer,
                                                 int insertionOffset,
                                                 int previousStart,
                                                 int outerStart,
                                                 IndentationScheme scheme) throws BadLocationException {
        int indentation;
        String type = scheme.getScheme();
        IDocument document = explorer.getDocument();

        if (type == IndentationScheme.DEFAULT) {
            // find the first no
            int lineStart = explorer.getDocument().getLineInformationOfOffset(previousStart).getOffset();
            int offset = previousStart;

            while (explorer.backwardSexpression(offset) && offset > lineStart) {
                previousStart = offset;
                offset = explorer.getSexpStart();
            }
            indentation = findColumn(document, previousStart);
        }
        else
            if (type == IndentationScheme.SEQUENCE || type == IndentationScheme.DEFINITION) {
                indentation = findColumn(document, outerStart) + 2;
            }
            else
                if (type == IndentationScheme.IF) {
                    indentation = findColumn(document, outerStart) + 4;
                }
                else
                    if (type == IndentationScheme.WITH) {
                        int previousCount = 0;
                        int offset = insertionOffset;

                        while (explorer.backwardSexpression(offset)) {
                            offset = explorer.getSexpStart();
                            previousCount++;
                            if (previousCount > scheme.getHint())
                                break;
                        }
                        if (previousCount > scheme.getHint())
                            indentation = findColumn(document, outerStart) + 2;
                        else
                            indentation = findColumn(document, outerStart) + 4;
                    }
                    else
                        indentation = findColumn(document, previousStart);
        return indentation;
    }

    private static int findColumn(IDocument document, int offset) throws BadLocationException {
        IRegion info = document.getLineInformationOfOffset(offset);
        int tabWidth = SchemePreferences.getTabWidth();
        
        int indent = 0;
        int index = info.getOffset();
        while(index < offset) {
            char ch = document.getChar(index++);
            if (ch == '\t')
                indent += tabWidth;
            else
                indent++;
        }
        return indent;
    }

    private void removeLeadingSpaces(IDocument document, DocumentCommand command) throws BadLocationException {
        command.length = indentationLength(document, command.offset);
    }

    public static int indentationLength(IDocument document, int offset) throws BadLocationException {
        int end = document.getLength();
        int length = 0;
        while (offset < end) {
            char ch = document.getChar(offset++);
            if (ch == ' ' || ch == '\t') {
                length++;
            }
            else {
                break;
            }
        }
        return length;
    }

    public static String makeIndentationString(int length) {
        StringBuffer buffer = new StringBuffer(0);
        if (length > 0) {
            while (length-- > 0)
                buffer.append(' ');
        }
        return buffer.toString();
    }
}