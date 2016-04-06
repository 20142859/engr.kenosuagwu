package com.votinginfoproject.VotingInformationProject.activities.homeActivity;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;
import com.votinginfoproject.VotingInformationProject.BuildConfig;
import com.votinginfoproject.VotingInformationProject.R;
import com.votinginfoproject.VotingInformationProject.models.CivicApiError;
import com.votinginfoproject.VotingInformationProject.models.Election;
import com.votinginfoproject.VotingInformationProject.models.VoterInfo;
import com.votinginfoproject.VotingInformationProject.models.api.interactors.CivicInfoInteractor;
import com.votinginfoproject.VotingInformationProject.models.api.requests.CivicInfoRequest;
import com.votinginfoproject.VotingInformationProject.models.api.requests.StopLightCivicInfoRequest;

import java.util.ArrayList;

/**
 * Created by marcvandehey on 3/31/16.
 */
public class HomePresenterImpl extends HomePresenter implements CivicInfoInteractor.CivicInfoCallback {

    private static final String TAG = HomePresenterImpl.class.getSimpleName();
    private static final String VOTER_INFO_KEY = "VOTER_INFO";
    private static final String ALL_PARTIES_KEY = "ALL_PARTIES";
    private CivicInfoInteractor mCivicInteractor;
    private boolean mTestRun = false;
    private VoterInfo mVoterInfo;
    private ArrayList<Election> mElections;
    private ArrayList<String> mParties;

    private int mSelectedElection;
    private int mSelectedParty;

    private String allPartiesString;

    public HomePresenterImpl(Context context) {
        this.mVoterInfo = null;

        mSelectedElection = 0;
        mSelectedParty = 0;

        allPartiesString = context.getString(R.string.fragment_home_all_parties);
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState != null) {
            String voterInfoString = savedState.getString(VOTER_INFO_KEY);

            Log.v(TAG, "Saved String: " + voterInfoString);

            if (voterInfoString != null && voterInfoString.length() > 0) {
                mVoterInfo = new Gson().fromJson(voterInfoString, VoterInfo.class);
            }

            allPartiesString = savedState.getString(ALL_PARTIES_KEY);
        }
    }

    @Override
    public void onAttachView(HomeView homeView) {
        super.onAttachView(homeView);

        //Recreate view with cached response
        if (mVoterInfo != null) {
            civicInfoResponse(mVoterInfo);
        }
    }

    @Override
    public void onDetachView() {
        super.onDetachView();

        cancelSearch();
    }

    @Override
    public void onSaveState(@NonNull Bundle state) {
        if (mVoterInfo != null) {
            String voterInfoString = new Gson().toJson(mVoterInfo);
            state.putString(VOTER_INFO_KEY, voterInfoString);
            state.putString(ALL_PARTIES_KEY, allPartiesString);
        }
    }

    @Override
    public void onDestroy() {
        setView(null);

        cancelSearch();
    }

    /**
     * For use when testing only.  Sets flag to indicate that we're testing the app, so it will
     * use the special test election ID for the query.
     */
    public void doTestRun() {
        mTestRun = true;
    }

    // Home Presenter Protocol

    @Override
    public void selectedElection(Context context, String address, int election) {
        if (mElections.size() > election) {
            mSelectedElection = election;
            getView().setElectionText(mElections.get(mSelectedElection).getName());

            searchElection(context, address, mElections.get(election).getId());
        }
    }

    @Override
    public void selectedParty(int party) {
        if (mParties.size() > party) {
            mSelectedParty = party;
            getView().setPartyText(mParties.get(mSelectedParty));
        }
    }

    @Override
    public void electionTextViewClicked() {
        ArrayList<String> list = new ArrayList<>();

        for (Election election : mElections) {
            list.add(election.getName());
        }

        getView().displayElectionPickerWithItems(list, mSelectedElection);
    }

    @Override
    public void partyTextViewClicked() {
        getView().displayPartyPickerWithItems(mParties, mSelectedParty);
    }

    @Override
    public void goButtonClicked() {
        Log.d(TAG, "Go Button Clicked");

        if (mVoterInfo != null && mVoterInfo.isSuccessful()) {
            //Filter Voting info and send to activity


            String filter = "";

            //If All Parties is not Selected
            if (mSelectedParty > 0) {
                filter = mParties.get(mSelectedParty);
            }

            getView().navigateToVoterInformationActivity(mVoterInfo, filter);
        }
    }

    @Override
    public void aboutButtonClicked() {
        Log.d(TAG, "About Button Clicked");

        if (mCivicInteractor == null) {
            getView().navigateToAboutActivity();
        }
    }

    @Override
    public void searchButtonClicked(@NonNull Context context, @NonNull String searchAddress) {
        Log.d(TAG, "Search Button Clicked");

        searchElection(context, searchAddress, "");
    }

    private void searchElection(@NonNull Context context, @NonNull String searchAddress, @NonNull String electionId) {
        if (mCivicInteractor == null) {
            getView().hideElectionPicker();
            getView().hidePartyPicker();
            getView().hideGoButton();

            mVoterInfo = null;
            mSelectedElection = 0;

            getView().showMessage(R.string.activity_home_status_loading);

            if (mTestRun) {
                electionId = "2000"; // test election ID (for use only in testing)
            }

            //TODO Determine if we need to search available elections before this point

            mCivicInteractor = new CivicInfoInteractor();

            CivicInfoRequest request;

            //Check if we are building with the Debug settings, if so attempt to use StopLight
            if (BuildConfig.DEBUG && context.getResources().getBoolean(R.bool.use_stoplight)) {
                searchAddress = context.getString(R.string.test_address);

                request = new StopLightCivicInfoRequest(context, electionId, searchAddress);

                //Set address to test string for directions api

                getView().overrideSearchAddress(searchAddress);
            } else {
                request = new CivicInfoRequest(context, electionId, searchAddress);
            }

            mCivicInteractor.enqueueRequest(request, this);
        }
    }

    private void cancelSearch() {
        if (mCivicInteractor != null) {
            mCivicInteractor.cancel(true);
            mCivicInteractor.onDestroy();

            mCivicInteractor = null;
        }
    }

    // Interactor Callback

    @Override
    public void civicInfoResponse(VoterInfo response) {
        if (response != null) {
            if (response.isSuccessful()) {
                mVoterInfo = response;

                getView().showGoButton();
                getView().hideStatusView();

                if (mVoterInfo.otherElections != null && mVoterInfo.otherElections.size() > 0) {
                    //Setup all elections data and show chooser

                    mElections = new ArrayList<>(mVoterInfo.otherElections);

                    //Add the default election to the front of the list.
                    mElections.add(0, mVoterInfo.election);

                    getView().showElectionPicker();
                    mSelectedElection = 0;
                    getView().setElectionText(mVoterInfo.election.getName());
                }

                mParties = mVoterInfo.getUniqueParties();
                mParties.add(0, allPartiesString);

                if (mParties.size() > 1) {
                    getView().setPartyText(mParties.get(0));
                    mSelectedParty = 0;
                    getView().showPartyPicker();
                }
            } else {
                getView().hideGoButton();

                CivicApiError error = response.getError();

                CivicApiError.Error error1 = error.errors.get(0);

                Log.e(TAG, "Civic API returned error: " + error.code + ": " +
                        error.message + " : " + error1.domain + " : " + error1.reason);

                if (CivicApiError.errorMessages.get(error1.reason) != null) {
                    int reason = CivicApiError.errorMessages.get(error1.reason);
                    getView().showMessage(reason);
                } else {
                    Log.e(TAG, "Unknown API error reason: " + error1.reason);
                    getView().showMessage(R.string.fragment_home_error_unknown);
                }
            }
        } else {
            Log.d(TAG, "API returned null response");
            getView().showMessage(R.string.fragment_home_error_unknown);
        }

        cancelSearch();
    }
}
