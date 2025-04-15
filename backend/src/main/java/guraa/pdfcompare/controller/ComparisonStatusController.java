package guraa.pdfcompare.controller;

import guraa.pdfcompare.service.ComparisonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/pdfs")
public class ComparisonStatusController {

    @Autowired
    private ComparisonService comparisonService;

    /**
     * Check if a comparison is ready.
     * This supports the HEAD method from api.js: checkComparisonStatus.
     *
     * @param comparisonId The comparison ID
     * @return 200 OK if ready, 202 Accepted if processing, 404 Not Found if not found
     */
    @RequestMapping(value = "/comparison/{comparisonId}", method = RequestMethod.HEAD)
    public ResponseEntity<?> checkComparisonStatus(@PathVariable String comparisonId) {
        try {
            boolean isReady = comparisonService.isComparisonCompleted(comparisonId);
            if (isReady) {
                return ResponseEntity.ok().build();
            } else if (comparisonService.isComparisonInProgress(comparisonId)) {
                return ResponseEntity.accepted().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error checking comparison status: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}