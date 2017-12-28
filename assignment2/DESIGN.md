# Design Document
A design document that describes how you designed your csc488.cup file. 
Explain the issues that arose with the source language reference grammar and how you resolved those issues.

## Issues and Resolutions
#### Shift/Reduce Conflict
When creating the grammer for the parser, the language reference allowed sequences of statements or sequences of declarations to be set in the following style:

> statement ::= statement statement ;

However, this would lead to a shift/reduce conflict. The LALR parser would first process 'statement', then assume two outcomes - 'statement' satisfies the grammar rule for the parse tree and reduce it into a single tree, or shift another statement to be processed to satisfy the 'statement statement' rule.
The ambiguity can be removed by adding a new rule in the form of:

> statement	::= statements
> 			      | statement statements
> 			      ;
> 
> statements   ::= ... ;

This solves the reduction issue since it is only possible to process 'statement' when there are no extra statements.

#### Reduce/Reduce Conflict
There were reduce/reduce conflicts in because the language reference allowed productions such as:

> expression ::= parametername
>              | functionname

Since both 'parametername' and 'functionname' produce 'IDENT' this results in a reduce/reduce error since the parser does not know whether to reduce to a 'parametername' or 'functionname'.

To resolve this issue we decided to replace all non-terminals that only produce 'IDENT' with 'IDENT' since it is not possible to disambiguate between them on a syntactical level.

#### Dangling Else Conflict
The dangling else problem also led to an issue:

> IF expression THEN statements ELSE statements

Would be ambiguous when there are nested statements. The rule can be nested as so - if a then (if b then s) else x - which would execute s when a and b is true and execute x if a is false or a is true and b is false. The ELSE would attach itself to the corresponding IFs and cause ambiguity. This can be solved by unassociating the ELSE with the IFs.
> precedence nonassoc ELSE;

## Unresolvable Issues

The language reference did not intend for function calls to be treated as statements. However, since both 'procedurename' and 'functionname' produce 'IDENT' it was not possible to disambiguate the two at a syntactical level. Therefore, a program that calls a function as a statement still passes parsing. We believe this issue requires semantic analysis to resolve.
