package edu.university.itsm.reporting;
import edu.university.itsm.incident.IncidentDtos;
import edu.university.itsm.shared.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
@RestController @RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;
    private final CurrentUser current;
    public ReportController(ReportService reports,CurrentUser current){this.reports=reports;this.current=current;}
    @GetMapping public ReportDtos.Report report(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute IncidentDtos.Search f){return reports.report(current.get(jwt),f);}
    @GetMapping("/services") public ReportDtos.Page<ReportDtos.ServiceHealth> services(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute IncidentDtos.Search f,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return reports.services(current.get(jwt),f,page,size);}
    @GetMapping("/technicians") public ReportDtos.Page<ReportDtos.Technician> technicians(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute IncidentDtos.Search f,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return reports.technicians(current.get(jwt),f,page,size);}
    @GetMapping(value="/incidents.csv",produces="text/csv") public void export(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute IncidentDtos.Search f,@RequestParam(defaultValue="createdAt") String sort,@RequestParam(defaultValue="desc") String direction,HttpServletResponse response)throws IOException{reports.export(current.get(jwt),f,sort,direction,response);}
}
