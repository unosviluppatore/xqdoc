/*
 * Copyright (c)2005 Elsevier, Inc.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The use of the Apache License does not indicate that this project is
 * affiliated with the Apache Software Foundation.
 */

package org.xqdoc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * This class knows hows to 'parse' through a string of text that consists of a
 * xqDoc Comment block. Longer term, this logic should probably be embedded in
 * the actual xqDoc grammar, but for now it resides here. Perhaps when my
 * experience with ANTLR grows, I will move this logic. The class does assume
 * that a xqDoc comment is separated into 'lines'.
 *
 * @author Darin McBeath
 * @version 1.0
 */
public class XQDocComment {

    // Buffer array for holding the current xqDoc comment block
    private StringBuffer[] xqDocCommentBlock = new StringBuffer[XQDOC_STATE_LAST];

    // Current xqDoc Comment State ... valid values are -1 to 9
    private int xqDocCommentState = -1;

    private int xqDocDescriptionLeadingSpaces = 0;

    // String of current xqDoc comment
    private String xqDocCommentString;

    // xqDoc XML tag for comments
    private static final String XQDOC_COMMENT_TAG = "comment";

    // Various xqDoc Comment States and Names
    private static final int XQDOC_STATE_DESCRIPTION = 0;

    private static final int XQDOC_STATE_AUTHOR = 1;

    private static final String XQDOC_COMMENT_AUTHOR = "@author";

    private static final int XQDOC_STATE_VERSION = 2;

    private static final String XQDOC_COMMENT_VERSION = "@version";

    private static final int XQDOC_STATE_PARAM = 3;

    private static final String XQDOC_COMMENT_PARAM = "@param";

    private static final int XQDOC_STATE_RETURN = 4;

    private static final String XQDOC_COMMENT_RETURN = "@return";

    private static final int XQDOC_STATE_ERROR = 5;

    private static final String XQDOC_COMMENT_ERROR = "@error";

    private static final int XQDOC_STATE_DEPRECATED = 6;

    private static final String XQDOC_COMMENT_DEPRECATED = "@deprecated";

    private static final int XQDOC_STATE_SEE = 7;

    private static final String XQDOC_COMMENT_SEE = "@see";

    private static final int XQDOC_STATE_SINCE = 8;

    private static final String XQDOC_COMMENT_SINCE = "@since";

    private static final int XQDOC_STATE_CUSTOM = 9;

    private static final String XQDOC_COMMENT_CUSTOM = "@custom";

    private static final int XQDOC_STATE_LAST = 10;

    private static final String BEGIN_XQDOC_COMMENT = "(:~";

    private static final String END_XQDOC_COMMENT = ":)";

    // The order of the following tags must match the order of the values
    // assigned to the various xqDoc comment states.
    private static final String[] XQDOC_STATE_TAG = { "description", "author",
            "version", "param", "return", "error", "deprecated", "see", "since", "custom" };

    /**
     * Initailize the XQDocComment object for processing of a xqDoc comment
     * block. This includes clearing the various buffers and setting the current
     * comment state to 'unknown'.
     *
     */
    public void clear() {
        xqDocCommentString = null;
        xqDocDescriptionLeadingSpaces = 0;
        xqDocCommentState = -1;
        for (int i = 0; i < xqDocCommentBlock.length; i++) {
            xqDocCommentBlock[i] = new StringBuffer(512);
        }
    }

    /**
     * Set the internal buffer with the specified xqDoc comment block. This
     * method is invoked (via XQDocContext) via the Parser for each xqDoc
     * comment block encountered while parsing the module.
     *
     * @param comment
     *            A xqDoc comment block
     */
    public void setComment(String comment) {
        xqDocCommentString = comment;
        xqDocDescriptionLeadingSpaces = 0;
    }

    /**
     * Loop through the array of string buffers for the xqDoc comment block and
     * construct a complete comment block.
     *
     * @return The serialized xqDoc XML for the current xqDoc comment block
     */
    public StringBuilder getXML() {
        StringBuilder sb = new StringBuilder(1024);
        if (xqDocCommentString != null) {
            buildXQDocCommentSection();
            sb.append(XQDocXML.buildBeginTag(XQDOC_COMMENT_TAG));
            for (int i = 0; i < xqDocCommentBlock.length; i++) {
                sb.append(xqDocCommentBlock[i]);
            }
            sb.append(XQDocXML.buildEndTag(XQDOC_COMMENT_TAG));
        }
        return sb;
    }

    private int leadingSpacesCount(String text) {
        String regex = "^\\s+";
        String trimmedString1 = text.replaceAll(regex, "");
        return text.length() - trimmedString1.length();
    }

    /**
     * Append the current comment line to the comment buffer associated with the
     * current xqDoc comment state.
     *
     * @param line
     *            Current line from module containing a xqDoc comment
     * @param index
     *            Current xqDoc comment state
     */
    private void xqDocCommentStateConcat(String line, int index) {
        int last = line.indexOf(END_XQDOC_COMMENT);
        if (last == -1) {
            last = line.length();
        }
        if (index == -1) {
            int i;
            if ((i = line.indexOf(BEGIN_XQDOC_COMMENT)) > -1) {
                String trimmedLine = line.substring(i + BEGIN_XQDOC_COMMENT.length(), last);
                int compareSize = XQDOC_STATE_TAG[xqDocCommentState].length() + 8;
                if (xqDocCommentBlock[xqDocCommentState].length() > compareSize) {
                    xqDocDescriptionLeadingSpaces = leadingSpacesCount(trimmedLine);
                    trimmedLine = trimmedLine.substring(xqDocDescriptionLeadingSpaces);
                }
                xqDocCommentBlock[xqDocCommentState].append(trimmedLine);
            } else if (line.matches("^\\s*:.*")) {
                i = line.indexOf(':');
                if (i < last) {
                    String trimmedLine = line.substring(i + 1, last);
                    int compareSize = XQDOC_STATE_TAG[xqDocCommentState].length() + 8;
                    if (xqDocCommentBlock[xqDocCommentState].length() > compareSize) {
                        xqDocCommentBlock[xqDocCommentState].append("\n");
                    } else {
                        xqDocDescriptionLeadingSpaces = leadingSpacesCount(trimmedLine);
                    }
                    trimmedLine = trimmedLine.length() >= xqDocDescriptionLeadingSpaces ? trimmedLine.substring(xqDocDescriptionLeadingSpaces) : trimmedLine;
                    xqDocCommentBlock[xqDocCommentState].append(trimmedLine);
                }
                // Get up to the closing comment
                else if (last != line.length()) {
                    int compareSize = XQDOC_STATE_TAG[xqDocCommentState].length() + 8;
                    if (xqDocCommentBlock[xqDocCommentState].length() > compareSize) {
                        xqDocCommentBlock[xqDocCommentState].append("\n");
                    }
                    xqDocCommentBlock[xqDocCommentState].append(line.substring(0, last));
                }
            } else {
                int compareSize = XQDOC_STATE_TAG[xqDocCommentState].length() + 8;
                if (xqDocCommentBlock[xqDocCommentState].length() > compareSize) {
                    xqDocCommentBlock[xqDocCommentState].append("\n");
                }
                xqDocCommentBlock[xqDocCommentState].append(line.substring(0, last));
            }
        } else {
            String trimmedLine = line.substring(index, last);
            xqDocDescriptionLeadingSpaces = leadingSpacesCount(trimmedLine);
            xqDocCommentBlock[xqDocCommentState].append(trimmedLine.trim());
        }
    }

    /**
     * Begin a new comment within the current xqDoc comment state by appending
     * this comment to the buffer associated with the current xqDoc comment
     * state.
     *
     */
    private void xqDocCommentStateBegin(String tag) {
        if (tag != null) {
            xqDocCommentBlock[xqDocCommentState].append(XQDocXML
                    .buildBeginTagWithTagAttribute(XQDOC_STATE_TAG[xqDocCommentState], tag));
        } else {
            xqDocCommentBlock[xqDocCommentState].append(XQDocXML
                    .buildBeginTag(XQDOC_STATE_TAG[xqDocCommentState]));
        }
        xqDocCommentBlock[xqDocCommentState].append("<![CDATA[");
    }

    /**
     * Close a comment within the current xqDoc comment state by appending this
     * comment to the buffer associated with the current xqDoc comment state.
     *
     */
    private void xqDocCommentStateClose() {
        String line = xqDocCommentBlock[xqDocCommentState].toString().replaceFirst("\\s++$", "");
        xqDocCommentBlock[xqDocCommentState] = new StringBuffer();
        xqDocCommentBlock[xqDocCommentState].append(line);
        xqDocCommentBlock[xqDocCommentState].append("]]>");
        xqDocCommentBlock[xqDocCommentState].append(XQDocXML
                .buildEndTag(XQDOC_STATE_TAG[xqDocCommentState]));
    }

    /**
     * Process the xqDoc comment block. This includes converting the String of
     * xqDoc comment (set by the parser) into a list of 'lines'. Each line will
     * then be iteratively processed by invoking processXQDocLine.
     *
     * @throws XQDocRuntimeException
     *             Problems processing the xqDoc comment
     */
    private void buildXQDocCommentSection()  {
        try {
            if (xqDocCommentString == null)
                return;

            BufferedReader br = new BufferedReader(new StringReader(
                    xqDocCommentString));
            String line = null;
            ArrayList lines = new ArrayList();
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            br.close();

            for (int i = 0; i < lines.size(); i++) {
                processXQDocLine((String) lines.get(i));
            }
        } catch (IOException ex) {
            throw new XQDocRuntimeException(
                    "Problems processing the comment block.", ex);
        }
    }

    /**
     * Process the line and determine the 'state' of the comment. In other
     * words, is this for an 'author', 'version', 'description', etc. Then,
     * append the comment information to the correct buffer (depending on the
     * state).
     *
     * @param line
     *            Current line of xqDoc comment
     */
    private void processXQDocLine(String line) {
        int index;
        if ((index = line.indexOf(XQDOC_COMMENT_PARAM)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_PARAM;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index + XQDOC_COMMENT_PARAM.length());
        } else if ((index = line.indexOf(XQDOC_COMMENT_RETURN)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_RETURN;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index + XQDOC_COMMENT_RETURN.length());
        } else if ((index = line.indexOf(XQDOC_COMMENT_ERROR)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_ERROR;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index + XQDOC_COMMENT_ERROR.length());
        } else if ((index = line.indexOf(XQDOC_COMMENT_DEPRECATED)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_DEPRECATED;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index
                    + XQDOC_COMMENT_DEPRECATED.length());
        } else if ((index = line.indexOf(XQDOC_COMMENT_SEE)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_SEE;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index + XQDOC_COMMENT_SEE.length());
        } else if ((index = line.indexOf(XQDOC_COMMENT_SINCE)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_SINCE;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index + XQDOC_COMMENT_SINCE.length());
        } else if ((index = line.indexOf(XQDOC_COMMENT_CUSTOM)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_CUSTOM;
            int offset = index + XQDOC_COMMENT_CUSTOM.length();
            String customString = line.substring(offset);
            String tag = null;
            if (customString.startsWith(":")) {
                offset++;
                customString = customString.substring(1);
                if (customString.contains(" ")) {
                    tag = customString.substring(0, customString.indexOf(' '));
                } else {
                    tag = customString;
                }
                offset += tag.length();
            }
            xqDocCommentStateBegin(tag);
            xqDocCommentStateConcat(line, offset);
        } else if ((index = line.indexOf(XQDOC_COMMENT_AUTHOR)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_AUTHOR;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index + XQDOC_COMMENT_AUTHOR.length());
        } else if ((index = line.indexOf(XQDOC_COMMENT_VERSION)) > -1) {
            xqDocCommentStateClose();
            xqDocCommentState = XQDOC_STATE_VERSION;
            xqDocCommentStateBegin(null);
            xqDocCommentStateConcat(line, index
                    + XQDOC_COMMENT_VERSION.length());
        } else {
            if (xqDocCommentState == -1) {
                xqDocCommentState = XQDOC_STATE_DESCRIPTION;
                xqDocCommentStateBegin(null);
            }
            // Concatenate to previous state
            xqDocCommentStateConcat(line, -1);
        }

        if (line.indexOf(END_XQDOC_COMMENT) > -1) {
            xqDocCommentStateClose();
        }
    }
}