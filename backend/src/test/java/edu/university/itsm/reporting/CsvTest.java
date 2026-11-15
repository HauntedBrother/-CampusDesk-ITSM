package edu.university.itsm.reporting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CsvTest {
    @Test void quotesCommasNewlinesAndNulls(){assertEquals("\"a,b\",\"say \"\"yes\"\"\",\"line\nnext\",\"\"\r\n",Csv.row("a,b","say \"yes\"","line\nnext",null));}
    @Test void neutralizesFormulasAfterLeadingWhitespace(){for(String value:new String[]{"=1+1","+cmd","-1+1","@SUM(A1)"," \t=1"})assertTrue(Csv.row(value).startsWith("\"'"));assertEquals("\"Normal text\"\r\n",Csv.row("Normal text"));}
}
