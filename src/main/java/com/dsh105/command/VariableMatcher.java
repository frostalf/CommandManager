/*
 * This file is part of CommandManager.
 *
 * CommandManager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CommandManager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CommandManager.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dsh105.command;

import com.dsh105.commodus.StringUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableMatcher {

    private static final Pattern SYNTAX_PATTERN = Pattern.compile("(<|\\[)([^>\\]]+)(?:>|\\])", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_SYNTAX_PATTERN = Pattern.compile("(?:<|\\[)(?:r:((?:(?!,n:.+)[^>\\]])+))(?:,n:([^>\\]]+))?(?:>|\\])", Pattern.CASE_INSENSITIVE);

    private String command;
    private String eventInput;

    private String syntaxPattern;
    private List<String> arguments;
    private ArrayList<Variable> variables;
    private HashMap<Variable, String> matchedArguments;

    public VariableMatcher(String command, String eventInput) {
        this.command = command;
        this.eventInput = eventInput;
        this.arguments = Arrays.asList(command.split("\\s"));
    }

    protected String buildVariableSyntax() {
        if (variables == null) {
            variables = new ArrayList<>();
        }
        String syntaxPattern = command;

        Matcher syntaxMatcher = SYNTAX_PATTERN.matcher(command);

        while (syntaxMatcher.find()) {
            int startIndex = arguments.indexOf(syntaxMatcher.group(0));

            Variable variable;
            Range range = new Range(startIndex, syntaxMatcher.group(2).endsWith("...") ? eventInput.length() - 1 : startIndex);

            Matcher regexMatcher = REGEX_SYNTAX_PATTERN.matcher(syntaxMatcher.group(0));
            if (regexMatcher.matches()) {
                String regex = regexMatcher.group(1);
                String name = regexMatcher.group(2);
                variable = new Variable(syntaxMatcher.group(0), regex, name == null ? regex : name.replace("...", ""), range);
            } else {
                variable = new Variable(syntaxMatcher.group(0), syntaxMatcher.group(2).replace("...", ""), range);
            }

            /*
             * Conditions:
             * If the regex exists, make use of it
             * Optional args can match something or nothing
             * Varargs style arguments can match anything, including spaces
             */
            syntaxPattern = syntaxPattern.replace(syntaxMatcher.group(0), (syntaxMatcher.group(2).endsWith("...") ? ("(" + (variable.getRegex().isEmpty() ? ".+" : variable.getRegex()) + ")") : "([^\\s]+)" + (syntaxMatcher.group(1).equals("[") ? "?" : "")));

            variables.add(variable);
        }

        this.syntaxPattern = syntaxPattern;
        return syntaxPattern;
    }

    public boolean matches() {
        if (syntaxPattern == null) {
            buildVariableSyntax();
        }
        return Pattern.compile("\\b" + syntaxPattern + "\\b").matcher(eventInput).matches();
    }

    public List<Variable> getVariables() {
        if (variables == null) {
            buildVariableSyntax();
        }
        return Collections.unmodifiableList(variables);
    }

    public Variable getVariableByName(String name) {
        return getVariableByName(name, false);
    }

    public Variable getVariableByName(String name, boolean ignoreCase) {
        for (Variable variable : getVariables()) {
            if (ignoreCase) {
                if (variable.getName().equalsIgnoreCase(name)) {
                    return variable;
                }
            } else if (variable.getName().equals(name)) {
                return variable;
            }
        }
        return null;
    }

    public Variable getVariableByRegex(String regex) {
        for (Variable variable : getVariables()) {
            if (variable.getRegex().equals(regex)) {
                return variable;
            }
        }
        return null;
    }

    public String getMatchedArgumentByVariableName(String name) {
        return getMatchedArgumentByVariableName(name, false);
    }

    public String getMatchedArgumentByVariableName(String name, boolean ignoreCase) {
        return getMatchedArguments().get(getVariableByName(name, ignoreCase));
    }

    public String getMatchedArgumentByVariableRegex(String regex) {
        return getMatchedArguments().get(getVariableByRegex(regex));
    }

    public Map<Variable, String> getMatchedArguments() {
        if (matchedArguments == null) {
            matchedArguments = new HashMap<>();

            String[] input = eventInput.split("\\s");
            for (Variable variable : getVariables()) {
                if (variable.getRange().getStartIndex() < input.length) {
                    if (variable.getRange().getEndIndex() <= variable.getRange().getStartIndex()) {
                        matchedArguments.put(variable, input[variable.getRange().getStartIndex()]);
                    } else {
                        matchedArguments.put(variable, StringUtil.combineArray(variable.getRange().getStartIndex(), " ", input));
                    }
                }
            }
        }
        return Collections.unmodifiableMap(matchedArguments);
    }

    public boolean testRegexVariables() {
        Matcher matcher = REGEX_SYNTAX_PATTERN.matcher(command);
        while (matcher.find()) {
            Variable variable = getVariableByName(matcher.group(matcher.group(2) == null ? 1 : 2));
            String regex = getMatchedArguments().get(variable);
            if (regex != null) {
                if (variable.getPattern().matcher(regex).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    public String replaceVariables(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String modified = input;
        for (Variable variable : getVariables()) {
            String matchedArgument = getMatchedArgumentByVariableName(variable.getName());
            modified = modified.replace(variable.getFullName(), matchedArgument == null ? "" : matchedArgument);
        }
        return modified;
    }
}