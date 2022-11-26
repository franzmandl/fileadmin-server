grammar Ticket;

fragment DIGIT0 : [0-9];

DATE : DIGIT0 DIGIT0 DIGIT0 DIGIT0 '-' DIGIT0 DIGIT0 '-' DIGIT0 DIGIT0;
TIME_HH_MM_SS : DIGIT0 DIGIT0 '.' DIGIT0 DIGIT0 '.' DIGIT0 DIGIT0;
TIME_HH_MM : DIGIT0 DIGIT0 '.' DIGIT0 DIGIT0;
DIGITS : DIGIT0+;
LOWERS : [a-z]+;
QUOTED_STRING : '\'' .*? '\'';
FILE_ENDING : ' - ' .+? EOF;
EVERYTHING_ELSE : .;

upper : 'A'|'B'|'C'|'D'|'E'|'F'|'G'|'H'|'I'|'J'|'K'|'L'|'M'|'N'|'O'|'P'|'Q'|'R'|'S'|'T'|'U'|'V'|'W'|'X'|'Y'|'Z';
usedSpecial : '+'|'-'|'&'|'('|')'|'.'|'!';

id : (upper | DIGITS | LOWERS)+;
string : (id | usedSpecial | DATE | EVERYTHING_ELSE)+;

start : trigger FILE_ENDING;

trigger : (dateTrigger | exprTrigger)
        | lhs=trigger lhsSpace=' '* and='&' rhsSpace=' '* rhs=trigger
        | lhs=trigger lhsSpace=' '* or='+' rhsSpace=' '* rhs=trigger
        | nestedTrigger;
nestedTrigger : markL='(' trigger markR=')';

dateTrigger : DATE time? effective? period?;
exprTrigger : id ('(' args? ')' time? effective? period? | ' ' (effective period? | period))?;
args : arg (' '+ arg)*;
arg : string | '{' trigger '}' | QUOTED_STRING;

time : (' ' (TIME_HH_MM | TIME_HH_MM_SS));
effective : 'E' negative='-'? (periodYearsMonthsDays | periodWeeks?);
period : 'P' exclm='!'? (periodYearsMonthsDays | periodWeeks?);

periodYearsMonthsDays : periodYears? periodMonths? periodDays?;
periodYears : value=DIGITS 'Y';
periodMonths : value=DIGITS 'M';
periodDays : value=DIGITS 'D';
periodWeeks : value=DIGITS 'W';
