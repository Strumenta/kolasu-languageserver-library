      * Calculates number of Fibonacci in an iterative way
     D NBR             S              8  0
     D RESULT          S              8  0 INZ(0)
     D COUNT           S              8  0
     D A               S              8  0 INZ(0)
     D B               S              8  0 INZ(1)
     D DSP             S             50
     FLOGFILE   Up   E             Disk
     FPHYFILE2  Up   E             Disk
      *--------------------------------------------------------------*
     C     FIB           BEGSR
     C                   CLEAR                   A
     C                   CLEAR                   B
     C                   SELECT
     C                   WHEN      NBR = 0
     C                   EVAL      RESULT = 0
     C                   WHEN      NBR = 1
     C                   EVAL      RESULT = 1
     C                   OTHER
     C                   FOR       COUNT = 2 TO NBR
     C                   EVAL      RESULT = A + B
     C                   EVAL      A = B
     C                   EVAL      B = RESULT
     C                   ENDFOR
     C                   ENDSL
     C                   ENDSR
      *--------------------------------------------------------------*
     C     *INZSR        BEGSR
     C                   EVAL      DSP='INZSR'
     C                   DSPLY     DSP
     C                   ENDSR
     C* Entry Point
     C     *LOVAL        SETLL     LOGFILE
     C                   READ      LOGFILE
     C                   DOW       NOT %EOF(LOGFILE)
     C                   EVAL      NBR    = %DEC(SNBR : 8 : 0)
     C                   EXSR      FIB
     C                   EVAL      DSP= 'FIBONACCI OF: ' + %TRIM(SNBR) +
     C                                 ' IS: ' + %CHAR(RESULT)
     C                   EVAL      SFIB   = RESULT
     C                   DSPLY     DSP
     C                   UPDATE    RCALCFIB
     C                   READ      LOGFILE
     C                   READ      PHYFILE2
     C                   ENDDO