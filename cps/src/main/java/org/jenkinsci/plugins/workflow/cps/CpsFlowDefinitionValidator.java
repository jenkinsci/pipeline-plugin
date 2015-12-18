/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import net.sf.json.JSONObject;

public final class CpsFlowDefinitionValidator {

    private static final String SUCCESS_KEY = "success";
    private static final String FAIL_KEY = "fail";

    private CpsFlowDefinitionValidator() {}

    public static List<CheckStatus> toCheckStatus(CompilationFailedException x) {
        List<CheckStatus> errors = new ArrayList<CheckStatus>();
        if (x instanceof MultipleCompilationErrorsException) {
            for (Object o : ((MultipleCompilationErrorsException) x).getErrorCollector().getErrors()) {
                if (o instanceof SyntaxErrorMessage) {
                    SyntaxException cause = ((SyntaxErrorMessage) o).getCause();
                    CheckStatus st = new CheckStatus(cause.getOriginalMessage(), FAIL_KEY);
                    st.setLine(cause.getLine());
                    st.setColumn(cause.getStartColumn());
                    errors.add(st);
                }
            }
        }
        if (errors.isEmpty()) {
            // Fallback to simple message
            CheckStatus st = new CheckStatus(x.getMessage(), FAIL_KEY);
            st.setLine(1);
            st.setColumn(0);
            errors.add(st);
        }
        return errors;
    }

    public static final class CheckStatus {

        public static final CheckStatus SUCCESS = new CheckStatus("", SUCCESS_KEY);

        private String message;
        private String status;
        private int line;
        private int column;

        public CheckStatus(String message, String status) {
            this.message = message;
            this.status = status;
        }
        public String getMessage() {
            return message;
        }
        public void setMessage(String message) {
            this.message = message;
        }
        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }
        public int getLine() {
            return line;
        }
        public void setLine(int line) {
            this.line = line;
        }
        public int getColumn() {
            return column;
        }
        public void setColumn(int column) {
            this.column = column;
        }

        public String toString() {
            return JSONObject.fromObject(this).toString();
        }
    }

}
