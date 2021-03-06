/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package com.mpobjects.bdparsii.eval;

import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mpobjects.bdparsii.tokenizer.ParseError;
import com.mpobjects.bdparsii.tokenizer.ParseException;
import com.mpobjects.bdparsii.tokenizer.Token;
import com.mpobjects.bdparsii.tokenizer.Tokenizer;

/**
 * Parses a given mathematical expression into an abstract syntax tree which can be evaluated.
 * <p>
 * Takes a string input as String or Reader which will be translated into an {@link Expression}. If one or more errors
 * occur, a {@link ParseException} will be thrown. The parser tries to continue as long a possible to provide good
 * insight into the errors within the expression.
 * <p>
 * This is a recursive descending parser which has a method per non-terminal.
 * <p>
 * Using this parser is as easy as:
 *
 * <pre>
 * Scope scope = Scope.create();
 * Variable a = scope.getVariable("a");
 * Expression expr = Parser.parse("3 + a * 4");
 * a.setValue(4);
 * System.out.println(expr.evaluate());
 * a.setValue(5);
 * System.out.println(expr.evaluate());
 * </pre>
 */
public class Parser {

    private final Scope scope;
    private List<ParseError> errors = new ArrayList<>();
    private Tokenizer tokenizer;
    private static Map<String, Function> functionTable;

    /*
     * Setup well known functions
     */
    static {
        functionTable = new TreeMap<>();

        registerFunction("sin", Functions.SIN);
        registerFunction("cos", Functions.COS);
        registerFunction("cot", Functions.COT);
        registerFunction("tan", Functions.TAN);
        registerFunction("sinh", Functions.SINH);
        registerFunction("cosh", Functions.COSH);
        registerFunction("coth", Functions.COTH);
        registerFunction("tanh", Functions.TANH);
        registerFunction("asin", Functions.ASIN);
        registerFunction("acos", Functions.ACOS);
        registerFunction("acot", Functions.ACOT);
        registerFunction("atan", Functions.ATAN);
        registerFunction("atan2", Functions.ATAN2);
        registerFunction("asinh", Functions.ASINH);
        registerFunction("acosh", Functions.ACOSH);
        registerFunction("acoth", Functions.ACOTH);
        registerFunction("atanh", Functions.ATANH);
        registerFunction("deg", Functions.DEG);
        registerFunction("rad", Functions.RAD);
        registerFunction("abs", Functions.ABS);
        registerFunction("round", Functions.ROUND);
        registerFunction("ceil", Functions.CEIL);
        registerFunction("floor", Functions.FLOOR);
        registerFunction("exp", Functions.EXP);
        registerFunction("ln", Functions.LN);
        registerFunction("log", Functions.LOG);
        registerFunction("log2", Functions.LOG2);
        registerFunction("sqrt", Functions.SQRT);
        registerFunction("pow", Functions.POW);
        registerFunction("min", Functions.MIN);
        registerFunction("max", Functions.MAX);
        registerFunction("rnd", Functions.RND);
        registerFunction("sign", Functions.SIGN);
        registerFunction("exponent", Functions.EXPONENT);
        registerFunction("mantissa", Functions.MANTISSA);
        registerFunction("if", Functions.IF);
        registerFunction("scale", Functions.SCALE);
    }

    /**
     * Used when the parser encountered an error
     */
    private static final Expression ERROR = mc -> null;

    protected Parser(Reader input, Scope scope) {
        this.scope = scope;
        tokenizer = new Tokenizer(input);
        tokenizer.setProblemCollector(errors);
    }

    /**
     * Registers a new function which can be referenced from within an expression.
     * <p>
     * A function must be registered before an expression is parsed in order to be visible.
     *
     * @param name the name of the function. If a function with the same name is already available, it will be
     *            overridden
     * @param function the function which is invoked as an expression is evaluated
     */
    public static void registerFunction(String name, Function function) {
        functionTable.put(name, function);
    }

    /**
     * Parses the given input into an expression.
     *
     * @param input the expression to be parsed
     * @return the resulting AST as expression
     * @throws ParseException if the expression contains one or more errors
     */
    public static Expression parse(String input) throws ParseException {
        return new Parser(new StringReader(input), new Scope()).parse();
    }

    /**
     * Parses the given input into an expression.
     *
     * @param input the expression to be parsed
     * @return the resulting AST as expression
     * @throws ParseException if the expression contains one or more errors
     */
    public static Expression parse(Reader input) throws ParseException {
        return new Parser(input, new Scope()).parse();
    }

    /**
     * Parses the given input into an expression.
     * <p>
     * Referenced variables will be resolved using the given Scope
     *
     * @param input the expression to be parsed
     * @param scope the scope used to resolve variables
     * @return the resulting AST as expression
     * @throws ParseException if the expression contains one or more errors
     */
    public static Expression parse(String input, Scope scope) throws ParseException {
        return new Parser(new StringReader(input), scope).parse();
    }

    /**
     * Parses the given input into an expression.
     * <p>
     * Referenced variables will be resolved using the given Scope
     *
     * @param input the expression to be parsed
     * @param scope the scope used to resolve variables
     * @return the resulting AST as expression
     * @throws ParseException if the expression contains one or more errors
     */
    public static Expression parse(Reader input, Scope scope) throws ParseException {
        return new Parser(input, scope).parse();
    }

    /**
     * Parses the expression in <tt>input</tt>
     *
     * @return the parsed expression
     * @throws ParseException if the expression contains one or more errors
     */
    protected Expression parse() throws ParseException {
        Expression result = expression().simplify(scope.getMathContext());
        if (tokenizer.current().isNotEnd()) {
            Token token = tokenizer.consume();
            errors.add(ParseError
                    .error(token, String.format("Unexpected token: '%s'. Expected an expression.", token.getSource())));
        }
        if (!errors.isEmpty()) {
            throw ParseException.create(errors);
        }
        return result;
    }

    /**
     * Parser rule for parsing an expression.
     * <p>
     * This is the root rule. An expression is a <tt>relationalExpression</tt> which might be followed by a logical
     * operator (&amp;&amp; or ||) and another <tt>expression</tt>.
     *
     * @return an expression parsed from the given input
     */
    protected Expression expression() {
        Expression left = relationalExpression();
        if (tokenizer.current().isSymbol("&&")) {
            tokenizer.consume();
            Expression right = expression();
            return reOrder(left, right, BinaryOperation.Op.AND);
        } else if (tokenizer.current().isSymbol("||")) {
            tokenizer.consume();
            Expression right = expression();
            return reOrder(left, right, BinaryOperation.Op.OR);
        } else {
            return left;
        }
    }

    /**
     * Parser rule for parsing a relational expression.
     * <p>
     * A relational expression is a <tt>term</tt> which might be followed by a relational operator (&lt;,&lt;=,...,&gt;)
     * and another <tt>relationalExpression</tt>.
     *
     * @return a relational expression parsed from the given input
     */
    protected Expression relationalExpression() {
        Expression left = term();
        if (tokenizer.current().isSymbol("<")) {
            tokenizer.consume();
            Expression right = relationalExpression();
            return reOrder(left, right, BinaryOperation.Op.LT);
        } else if (tokenizer.current().isSymbol("<=")) {
            tokenizer.consume();
            Expression right = relationalExpression();
            return reOrder(left, right, BinaryOperation.Op.LT_EQ);
        } else if (tokenizer.current().isSymbol("=")) {
            tokenizer.consume();
            Expression right = relationalExpression();
            return reOrder(left, right, BinaryOperation.Op.EQ);
        } else if (tokenizer.current().isSymbol(">=")) {
            tokenizer.consume();
            Expression right = relationalExpression();
            return reOrder(left, right, BinaryOperation.Op.GT_EQ);
        } else if (tokenizer.current().isSymbol(">")) {
            tokenizer.consume();
            Expression right = relationalExpression();
            return reOrder(left, right, BinaryOperation.Op.GT);
        } else if (tokenizer.current().isSymbol("!=")) {
            tokenizer.consume();
            Expression right = relationalExpression();
            return reOrder(left, right, BinaryOperation.Op.NEQ);
        } else {
            return left;
        }
    }

    /**
     * Parser rule for parsing a term.
     * <p>
     * A term is a <tt>product</tt> which might be followed by + or - as operator and another <tt>term</tt>.
     *
     * @return a term parsed from the given input
     */
    protected Expression term() {
        Expression left = product();
        if (tokenizer.current().isSymbol("+")) {
            tokenizer.consume();
            Expression right = term();
            return reOrder(left, right, BinaryOperation.Op.ADD);
        } else if (tokenizer.current().isSymbol("-")) {
            tokenizer.consume();
            Expression right = term();
            return reOrder(left, right, BinaryOperation.Op.SUBTRACT);
        } else if (tokenizer.current().isNumber() && tokenizer.current().getContents().startsWith("-")) {
            tokenizer.current().setContent(tokenizer.current().getContents().substring(1));
            Expression right = term();
            return reOrder(left, right, BinaryOperation.Op.SUBTRACT);
        } else {
            return left;
        }
    }

    /**
     * Parser rule for parsing a product.
     * <p>
     * A product is a <tt>power</tt> which might be followed by *, / or % as operator and another <tt>product</tt>.
     *
     * @return a product parsed from the given input
     */
    protected Expression product() {
        Expression left = power();
        if (tokenizer.current().isSymbol("*")) {
            tokenizer.consume();
            Expression right = product();
            return reOrder(left, right, BinaryOperation.Op.MULTIPLY);
        } else if (tokenizer.current().isSymbol("/")) {
            tokenizer.consume();
            Expression right = product();
            return reOrder(left, right, BinaryOperation.Op.DIVIDE);
        } else if (tokenizer.current().isSymbol("%")) {
            tokenizer.consume();
            Expression right = product();
            return reOrder(left, right, BinaryOperation.Op.MODULO);
        } else {
            return left;
        }
    }

    /*
     * Reorders the operands of the given operation in order to generate a "left handed" AST which performs evaluations
     * in natural order (from left to right).
     */
    protected Expression reOrder(Expression left, Expression right, BinaryOperation.Op op) {
        if (right instanceof BinaryOperation) {
            BinaryOperation rightOp = (BinaryOperation) right;
            if (!rightOp.isSealed() && rightOp.getOp().getPriority() == op.getPriority()) {
                replaceLeft(rightOp, left, op);
                return right;
            }
        }
        return new BinaryOperation(scope.getMathContext(), op, left, right);
    }

    protected void replaceLeft(BinaryOperation target, Expression newLeft, BinaryOperation.Op op) {
        if (target.getLeft() instanceof BinaryOperation) {
            BinaryOperation leftOp = (BinaryOperation) target.getLeft();
            if (!leftOp.isSealed() && leftOp.getOp().getPriority() == op.getPriority()) {
                replaceLeft(leftOp, newLeft, op);
                return;
            }
        }
        target.setLeft(new BinaryOperation(scope.getMathContext(), op, newLeft, target.getLeft()));
    }

    /**
     * Parser rule for parsing a power.
     * <p>
     * A power is an <tt>atom</tt> which might be followed by ^ or ** as operator and another <tt>power</tt>.
     *
     * @return a power parsed from the given input
     */
    protected Expression power() {
        Expression left = atom();
        if (tokenizer.current().isSymbol("^") || tokenizer.current().isSymbol("**")) {
            tokenizer.consume();
            Expression right = power();
            return reOrder(left, right, BinaryOperation.Op.POWER);
        } else {
            return left;
        }
    }

    /**
     * Parser rule for parsing an atom.
     * <p>
     * An atom is either a numeric constant, an <tt>expression</tt> in brackets, an <tt>expression</tt> surrounded by |
     * to signal the absolute function, an identifier to signal a variable reference or an identifier followed by a
     * bracket to signal a function call.
     *
     * @return an atom parsed from the given input
     */
    protected Expression atom() {
        if (tokenizer.current().isSymbol("-")) {
            tokenizer.consume();
            BinaryOperation result = new BinaryOperation(scope.getMathContext(),
                                                         BinaryOperation.Op.SUBTRACT,
                                                         new Constant(BigDecimal.ZERO),
                                                         atom());
            result.seal();
            return result;
        }
        if (tokenizer.current().isSymbol("+") && tokenizer.next().isSymbol("(")) {
            // Support for brackets with a leading + like "+(2.2)" in this case we simply ignore the
            // + sign
            tokenizer.consume();
        }

        if (tokenizer.current().isSymbol("(")) {
            tokenizer.consume();
            Expression result = expression();
            if (result instanceof BinaryOperation) {
                ((BinaryOperation) result).seal();
            }
            expect(Token.TokenType.SYMBOL, ")");
            return result;
        } else if (tokenizer.current().isSymbol("|")) {
            tokenizer.consume();
            FunctionCall call = new FunctionCall(scope.getMathContext());
            call.addParameter(expression());
            call.setFunction(Functions.ABS);
            expect(Token.TokenType.SYMBOL, "|");
            return call;
        } else if (tokenizer.current().isIdentifier()) {
            if (tokenizer.next().isSymbol("(")) {
                return functionCall();
            }
            Token variableName = tokenizer.consume();
            try {
                return new VariableReference(scope.getVariable(variableName.getContents()));
            } catch (@SuppressWarnings("UnusedCatchParameter") IllegalArgumentException e) {
                errors.add(ParseError.error(variableName,
                                            String.format("Unknown variable: '%s'", variableName.getContents())));
                return new Constant(BigDecimal.ZERO);
            }
        } else {
            return literalAtom();
        }
    }

    /**
     * Parser rule for parsing a literal atom.
     * <p>
     * An literal atom is a numeric constant.
     *
     * @return an atom parsed from the given input
     */
    private Expression literalAtom() {
        if (tokenizer.current().isSymbol("+") && tokenizer.next().isNumber()) {
            // Parse numbers with a leading + sign like +2.02 by simply ignoring the +
            tokenizer.consume();
        }
        if (tokenizer.current().isNumber()) {
            BigDecimal value = new BigDecimal(tokenizer.consume().getContents());
            if (tokenizer.current().is(Token.TokenType.ID)) {
                String quantifier = tokenizer.current().getContents().intern();
                switch (quantifier) {
                    case "n":
                        value = value.divide(BigDecimal.valueOf(1000000000), scope.getMathContext());
                        tokenizer.consume();
                        break;
                    case "u":
                        value = value.divide(BigDecimal.valueOf(1000000), scope.getMathContext());
                        tokenizer.consume();
                        break;
                    case "m":
                        value = value.divide(BigDecimal.valueOf(1000), scope.getMathContext());
                        tokenizer.consume();
                        break;
                    case "k":
                    case "K":
                        value = value.multiply(BigDecimal.valueOf(1000), scope.getMathContext());
                        tokenizer.consume();
                        break;
                    case "M":
                        value = value.multiply(BigDecimal.valueOf(1000000), scope.getMathContext());
                        tokenizer.consume();
                        break;
                    case "G":
                        value = value.multiply(BigDecimal.valueOf(1000000000), scope.getMathContext());
                        tokenizer.consume();
                        break;
                    default:
                        Token token = tokenizer.consume();
                        errors.add(ParseError
                                .error(token,
                                       String.format("Unexpected token: '%s'. Expected a valid quantifier.",
                                                     token.getSource())));
                }
            }
            return new Constant(value);
        } else {
            Token token = tokenizer.consume();
            errors.add(ParseError
                    .error(token, String.format("Unexpected token: '%s'. Expected an expression.", token.getSource())));
            return ERROR;
        }
    }

    /**
     * Parses a function call.
     *
     * @return the function call as Expression
     */
    protected Expression functionCall() {
        FunctionCall call = new FunctionCall(scope.getMathContext());
        Token funToken = tokenizer.consume();
        Function fun = functionTable.get(funToken.getContents());
        if (fun == null) {
            errors.add(ParseError.error(funToken, String.format("Unknown function: '%s'", funToken.getContents())));
        }
        call.setFunction(fun);
        tokenizer.consume();
        while (!tokenizer.current().isSymbol(")") && tokenizer.current().isNotEnd()) {
            if (!call.getParameters().isEmpty()) {
                expect(Token.TokenType.SYMBOL, ",");
            }
            call.addParameter(expression());
        }
        expect(Token.TokenType.SYMBOL, ")");

        if (fun == null) {
            return ERROR;
        } else if (call.getParameters().size() != fun.getNumberOfArguments() && fun.getNumberOfArguments() >= 0) {
            errors.add(ParseError
                    .error(funToken,
                           String.format("Number of arguments for function '%s' do not match. Expected: %d, Found: %d",
                                         funToken.getContents(),
                                         fun.getNumberOfArguments(),
                                         call.getParameters().size())));
            return ERROR;
        } else {
            return call;
        }
    }

    /**
     * Signals that the given token is expected.
     * <p>
     * If the current input is pointing at the specified token, it will be consumed. If not, an error will be added to
     * the error list and the input remains unchanged.
     *
     * @param type the type of the expected token
     * @param trigger the trigger of the expected token
     */
    protected void expect(Token.TokenType type, String trigger) {
        if (tokenizer.current().matches(type, trigger)) {
            tokenizer.consume();
        } else {
            errors.add(ParseError.error(tokenizer.current(),
                                        String.format("Unexpected token '%s'. Expected: '%s'",
                                                      tokenizer.current().getSource(),
                                                      trigger)));
        }
    }
}
