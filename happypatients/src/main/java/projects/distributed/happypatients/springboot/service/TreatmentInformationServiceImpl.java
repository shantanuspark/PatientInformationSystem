package projects.distributed.happypatients.springboot.service;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import projects.distributed.happypatients.converters.PatientConverter;
import projects.distributed.happypatients.converters.TreatmentConverter;
import projects.distributed.happypatients.dao.PatientDAO;
import projects.distributed.happypatients.dao.TreatmentDAO;
import projects.distributed.happypatients.springboot.cache.CacheConnector;
import projects.distributed.happypatients.springboot.model.Patient;
import projects.distributed.happypatients.springboot.model.TreatmentInformation;
import projects.distributed.happypatients.springboot.policy.PolicyEngine;
import projects.distributed.happypatients.springboot.utilities.GeneralUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TreatmentInformationServiceImpl implements TreatmentInformationService {

    static final Logger LOG = LoggerFactory.getLogger(TreatmentInformationServiceImpl.class);

    @Autowired
    TreatmentDAO treatmentDAO;

    @Autowired
    PatientDAO patientDAO;

    @Autowired
    PatientConverter patientConverter;

    @Autowired
    TreatmentConverter treatmentConverter;

    @Autowired
    CacheConnector cacheConnector;

    @Autowired
    PolicyEngine policyEngine;

    @Override
    public boolean addTreatmentInformation(TreatmentInformation treatmentInformation) {
        LOG.info("Adding treatment information for {}.", treatmentInformation.getPatientId());
        boolean isSuccess = treatmentDAO.createTreatment(UUID.fromString(treatmentInformation.getPatientId()),
                treatmentInformation.getMedicalCondition(), treatmentInformation.getDiagnosis(),
                treatmentInformation.getDoctorName(), treatmentInformation.getEndDate(),
                treatmentInformation.getReport(), treatmentInformation.getStartDate(),
                treatmentInformation.getTreatmentStatus().toString());

        if (isSuccess) {
            LOG.info("Treatment information add successful to database.");
            Patient patient = patientConverter.convert(patientDAO.getPatient(UUID.fromString(treatmentInformation.getPatientId())).one());
            if (patient != null) {
                patient.setTreatments(getTreatmentInformation(treatmentInformation.getPatientId()));
                LOG.info("Treatment information updated in patient record.");
                savePatientToCache(patient);
            }
        }
        return isSuccess;
    }

    @Override
    public List<TreatmentInformation> getTreatmentInformation(String id) {
        List<TreatmentInformation> treatmentInformations = new ArrayList<>();
        ResultSet results = treatmentDAO.getPatientsTreatment(UUID.fromString(id));
        for (Row row : results) {
            treatmentInformations.add(treatmentConverter.convert(row));
        }
        return treatmentInformations;
    }

    @Override
    public boolean deletePatientTreatment(String id) {
        LOG.info("Deleting treatment information for patient {}.", id);
        boolean isSuccess = treatmentDAO.deletePatientTreatment(UUID.fromString(id));
        cacheConnector.removePatientFromCache(id);
        if (isSuccess) {
            LOG.info("Delete treatment information in database successful.");
            Patient patient = patientConverter.convert(patientDAO.getPatient(UUID.fromString(id)).one());
            patient.setTreatments(getTreatmentInformation(id));
            savePatientToCache(patient);
        }

        return isSuccess;
    }

    @Override
    public boolean deletePatientTreatment(String id, String medicalCond) {
        LOG.info("Deleting treatment information for patient {}.", id);
        boolean isSuccess = treatmentDAO.deletePatientTreatment(UUID.fromString(id), medicalCond);
        cacheConnector.removePatientFromCache(id);
        if (isSuccess) {
            LOG.info("Delete treatment information in database successful.");
            Patient patient = patientConverter.convert(patientDAO.getPatient(UUID.fromString(id)).one());
            patient.setTreatments(getTreatmentInformation(id));
            savePatientToCache(patient);
        }

        return isSuccess;
    }

    @Override
    public boolean updateTreatment(TreatmentInformation treatmentInformation) {
        LOG.info("Updating treatment information for patient {}.", treatmentInformation.getPatientId());
        boolean isSuccess = treatmentDAO.updatePatient(UUID.fromString(treatmentInformation.getPatientId()),
                treatmentInformation.getMedicalCondition(), treatmentInformation.getDiagnosis(),
                treatmentInformation.getDoctorName(), treatmentInformation.getEndDate(),
                treatmentInformation.getReport(), treatmentInformation.getStartDate(),
                treatmentInformation.getTreatmentStatus().toString());

        if (isSuccess) {
        	Patient patient = patientConverter.convert(patientDAO.getPatient(UUID.fromString(treatmentInformation.getPatientId())).one());
        	patient.setTreatments(getTreatmentInformation(treatmentInformation.getPatientId()));
            savePatientToCache(patient);
        }
        return isSuccess;
    }

    private void savePatientToCache(Patient patient) {
    	cacheConnector.removePatientFromCache(patient.getId());
    	if(isPatientEligibleToBeCached(patient)) {
    		cacheConnector.savePatientToCache(patient.getId(), patient);
    	}
    }
    
    public boolean isPatientEligibleToBeCached(Patient patient) {
    	String currentPolicy = policyEngine.retrievePolicy();
    	for(TreatmentInformation treatmentInformation:patient.getTreatmentInformation()) {
    		if (treatmentInformation.getTreatmentStatus().toString().equals(currentPolicy) && GeneralUtilities.getYear(treatmentInformation.getStartDate()) >= 2000) {
    			return true;
    		}
    	}
    	return false;
    }

}
