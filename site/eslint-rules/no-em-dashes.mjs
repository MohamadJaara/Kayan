export default {
  meta: {
    type: 'suggestion',
    docs: { description: 'Disallow em dashes in source code, comments, and documentation.' },
    schema: [],
    messages: {
      forbidden: 'Em dashes (U+2014) are not allowed. Use a hyphen or rewrite the sentence.',
    },
  },
  create(context) {
    const { sourceCode } = context;
    for (const match of sourceCode.text.matchAll(/\u2014/gu)) {
      const start = sourceCode.getLocFromIndex(match.index);
      context.report({
        loc: { start, end: { line: start.line, column: start.column + 1 } },
        messageId: 'forbidden',
      });
    }
    return {};
  },
};
