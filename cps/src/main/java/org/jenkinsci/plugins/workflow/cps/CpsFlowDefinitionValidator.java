package org.jenkinsci.plugins.workflow.cps;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import net.sf.json.JSONObject;

public final class CpsFlowDefinitionValidator {

    private CpsFlowDefinitionValidator() {}

    public static List<CheckStatus> toCheckStatus(CompilationFailedException x) {
        List<CheckStatus> errors = new ArrayList<CheckStatus>();
        if (x instanceof MultipleCompilationErrorsException) {
            for (Object o : ((MultipleCompilationErrorsException) x).getErrorCollector().getErrors()) {
                if (o instanceof SyntaxErrorMessage) {
                    SyntaxException cause = ((SyntaxErrorMessage) o).getCause();
                    CheckStatus st = new CheckStatus(cause.getOriginalMessage(), "fail");
                    st.setLine(cause.getLine());
                    st.setColumn(cause.getStartColumn());
                    errors.add(st);
                }
            }
        }
        if (errors.isEmpty()) {
            // Fallback to simple message
            CheckStatus st = new CheckStatus(x.getMessage(), "fail");
            st.setLine(0);
            st.setColumn(0);
            errors.add(st);
        }
        return errors;
    }

    public static final class CheckStatus {

        public static final CheckStatus SUCCESS = new CheckStatus("", "success");

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
