/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2017 Aaron Madlon-Kay
               Home page: http://www.omegat.org/
               Support center: http://groups.yahoo.com/group/OmegaT/

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.gui.issues;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.omegat.core.Core;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.data.TMXEntry;
import org.omegat.gui.glossary.GlossaryEntry;
import org.omegat.gui.glossary.TransTips;
import org.omegat.util.FileUtil;
import org.omegat.util.OStrings;
import org.omegat.util.gui.Styles.EditorColor;

/**
 * A provider for terminology issues.
 *
 * @author Aaron Madlon-Kay
 *
 */
class TerminologyIssueProvider implements IIssueProvider {

    @Override
    public String getName() {
        return OStrings.getString("ISSUES_TERMINOLOGY_PROVIDER_NAME");
    }

    @Override
    public String getId() {
        return getClass().getCanonicalName();
    }

    @Override
    public List<IIssue> getIssues(SourceTextEntry sourceEntry, TMXEntry tmxEntry) {
        List<GlossaryEntry> entries = Core.getGlossaryManager().searchSourceMatches(sourceEntry.getSrcText());
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        return entries.stream().map(e -> {
            List<String> trgTerms = Core.getGlossaryManager().searchTargetMatches(tmxEntry.translation, e);
            return trgTerms.isEmpty() ? new TerminologyIssue(sourceEntry, tmxEntry, e) : null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * A class representing misspellings in a translation. One instance holds
     * all misspelled tokens for the given source/target text pair.
     *
     * @author Aaron Madlon-Kay
     *
     */
    static class TerminologyIssue implements IIssue {
        private static final Icon ICON = new SimpleColorIcon(EditorColor.COLOR_TERMINOLOGY.getColor());
        private static final AttributeSet ERROR_STYLE;
        static {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, EditorColor.COLOR_TERMINOLOGY.getColor());
            StyleConstants.setBold(attr, true);
            ERROR_STYLE = attr;
        }

        private final SourceTextEntry ste;
        private final TMXEntry tmxEntry;
        private final GlossaryEntry glossaryEntry;

        TerminologyIssue(SourceTextEntry ste, TMXEntry tmxEntry, GlossaryEntry glossaryEntry) {
            this.ste = ste;
            this.tmxEntry = tmxEntry;
            this.glossaryEntry = glossaryEntry;
        }

        @Override
        public Icon getIcon() {
            return ICON;
        }

        @Override
        public String getTypeName() {
            return OStrings.getString("ISSUES_TERMINOLOGY_TYPE");
        }

        @Override
        public int getSegmentNumber() {
            return ste.entryNum();
        }

        @Override
        public String getDescription() {
            // Trivial situation with just one term/origin
            String[] origins = glossaryEntry.getOrigins(false);
            String[] targetTerms = glossaryEntry.getLocTerms(false);
            if (targetTerms.length == 1) {
                String origin = FileUtil.getUniqueNames(Arrays.asList(origins[0])).get(0);
                return OStrings.getString("ISSUES_TERMINOLOGY_DESCRIPTION", origin, glossaryEntry.getSrcText(),
                        targetTerms[0]);
            }

            // Multiple terms, but just one origin
            String tDelim = OStrings.getString("ISSUES_TERMINOLOGY_TERM_DELIMITER");
            String[] uniqueOrigins = glossaryEntry.getOrigins(true);
            String[] uniqueTerms = glossaryEntry.getLocTerms(true);
            if (uniqueOrigins.length == 1) {
                List<String> formattedTerms = Arrays.asList(uniqueTerms);
                formattedTerms.replaceAll(t -> OStrings.getString("ISSUES_TERMINOLOGY_TERM_TEMPLATE", t));
                String origin = FileUtil.getUniqueNames(Arrays.asList(uniqueOrigins[0])).get(0);
                return OStrings.getString("ISSUES_TERMINOLOGY_DESCRIPTION_MULTI", origin, glossaryEntry.getSrcText(),
                        String.join(tDelim, formattedTerms));
            }

            // Multiple terms with multiple origins
            List<String> uniqueOriginsList = Arrays.asList(uniqueOrigins);
            String oDelim = OStrings.getString("ISSUES_TERMINOLOGY_ORIGIN_DELIMITER");
            String iDelim = OStrings.getString("ISSUES_TERMINOLOGY_TERM_ORIGIN_INDEX_DELIMITER");
            String originStr = String.join(oDelim, FileUtil.getUniqueNames(uniqueOriginsList));
            List<String> formattedTerms = new ArrayList<>(uniqueTerms.length);
            for (int i = 0; i < uniqueTerms.length; i++) {
                String term = uniqueTerms[i];
                // Collect unique term origins
                List<String> termOrigins = new ArrayList<>(uniqueOrigins.length);
                for (int j = 0; j < targetTerms.length; j++) {
                    if (term.equals(targetTerms[j])) {
                        String origin = origins[j];
                        if (!termOrigins.contains(origin)) {
                            termOrigins.add(origin);
                        }
                    }
                }
                // Collect indices of origins
                List<String> originIndices = new ArrayList<>(uniqueOrigins.length);
                for (String origin : termOrigins) {
                    int index = uniqueOriginsList.indexOf(origin) + 1;
                    originIndices.add(String.valueOf(index));
                }
                formattedTerms.add(OStrings.getString("ISSUES_TERMINOLOGY_TERM_MULTIORIGIN_TEMPLATE", term,
                        String.join(iDelim, originIndices)));
            }
            return OStrings.getString("ISSUES_TERMINOLOGY_DESCRIPTION_MULTI", originStr, glossaryEntry.getSrcText(),
                    String.join(tDelim, formattedTerms));
        }

        @Override
        public Component getDetailComponent() {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(getSplitPanel(), BorderLayout.CENTER);
            panel.add(getOriginLabel(), BorderLayout.SOUTH);
            return panel;
        }

        private Component getSplitPanel() {
            IssueDetailSplitPanel splitPanel = new IssueDetailSplitPanel();
            splitPanel.firstTextPane.setText(ste.getSrcText());
            splitPanel.lastTextPane.setText(tmxEntry.translation);
            StyledDocument doc = splitPanel.firstTextPane.getStyledDocument();
            TransTips.search(ste.getSrcText(), glossaryEntry, (ge, start, end) -> {
                doc.setCharacterAttributes(start, end - start, ERROR_STYLE, false);
            });
            splitPanel.setMinimumSize(new Dimension(0, splitPanel.firstTextPane.getFont().getSize() * 6));
            return splitPanel;
        }

        private Component getOriginLabel() {
            String delim = OStrings.getString("ISSUES_TERMINOLOGY_ORIGIN_DETAIL_DELIMITER");
            String[] origins = glossaryEntry.getOrigins(true);
            String originDesc;
            if (origins.length == 1) {
                originDesc = OStrings.getString("ISSUES_TERMINOLOGY_ORIGIN", origins[0]);
            } else {
                List<String> formattedOrigins = new ArrayList<>(origins.length);
                for (int i = 0; i < origins.length; i++) {
                    formattedOrigins
                            .add(OStrings.getString("ISSUES_TERMINOLOGY_ORIGIN_DETAIL_TEMPLATE", i + 1, origins[i]));
                }
                originDesc = OStrings.getString("ISSUES_TERMINOLOGY_ORIGINS", String.join(delim, formattedOrigins));
            }
            JTextArea originLabel = new JTextArea(originDesc);
            originLabel.setEditable(false);
            originLabel.setOpaque(false);
            originLabel.setLineWrap(true);
            originLabel.setWrapStyleWord(true);
            originLabel.setFont(UIManager.getFont("Label.font"));
            originLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            return originLabel;
        }
    }
}
