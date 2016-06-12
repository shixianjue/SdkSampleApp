package com.avaya.sdksampleapp;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.avaya.clientservices.common.DataRetrieval;
import com.avaya.clientservices.common.DataRetrievalListener;
import com.avaya.clientservices.common.DataSet;
import com.avaya.clientservices.common.DataSetChangeListener;
import com.avaya.clientservices.common.DataSetChangeType;
import com.avaya.clientservices.contact.Contact;
import com.avaya.clientservices.contact.ContactService;
import com.avaya.clientservices.contact.ContactSourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * ContactsFragment is used to show local contacts
 */
public class ContactsFragment extends Fragment implements DataRetrievalListener<Contact>, DataSetChangeListener<Contact> {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private DataRetrieval<Contact> dataRetrieval;

    private final List<String> contacts = new ArrayList<>();
    private ArrayAdapter<String> contactListAdapter;
    private DataSet<Contact> dataSet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "Fragment#onCreate()");
        super.onCreate(savedInstanceState);

        // SDK API call. Getting contact service
        ContactService contactService = SDKManager.getInstance(getActivity()).getUser().getContactService();
        // Creating DataRetrieval object that will receive all contacts
        dataRetrieval = contactService.getContacts(ContactSourceType.LOCAL);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "Fragment#onCreateView()");
        return inflater.inflate(R.layout.contacts_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "Fragment#onViewCreated()");
        super.onViewCreated(view, savedInstanceState);
        // Initializing ListView that is used to show contact list
        final ListView contactsListView = (ListView) view.findViewById(R.id.contacts_list);
        // Initializing ListView adapter
        contactListAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, contacts);
        contactsListView.setAdapter(contactListAdapter);
    }

    @Override
    public void onStart() {
        Log.d(LOG_TAG, "Fragment#onStart()");
        super.onStart();
        // Adding data retrieval listener in order to have retrieval completion trigger
        dataRetrieval.addDataRetrievalListener(this);
    }

    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "Fragment#onDestroyView()");
        super.onDestroyView();
        if (dataSet != null) {
            // Remove DataSetChange listener when fragment destroyed as we reload list in onCreate method when open fragment next time
            dataSet.removeDataSetChangeListener(this);
        }
        contacts.clear();
    }

    /*
     * DataRetrievalListener section
     */
    @Override
    public void onDataRetrievalProgress(DataRetrieval<Contact> dataRetrieval, boolean determinate, int numRetrieved, int total) {
        Log.d(LOG_TAG, "onDataRetrievalProgress");
    }

    // onDataRetrievalComplete is executed when DataRetrieval object receive all contacts
    @Override
    public void onDataRetrievalComplete(DataRetrieval<Contact> dataRetrieved) {
        Log.d(LOG_TAG, "onDataRetrievalComplete");
        // Remove DataRetrievalListener once contact loading completed
        dataRetrieval.removeDataRetrievalListener(this);

        dataSet = dataRetrieved.getDataSet();
        Log.d(LOG_TAG, "Loaded " + dataSet.size() + " contacts");

        //Add all retrieved contacts on screen
        for (Contact contact : dataSet) {
            contacts.add(contact.getNativeDisplayName().getValue());
        }

        //Let adapter know that contact list updated. It will redraw ListView on UI with updated list
        contactListAdapter.notifyDataSetChanged();

        //Add DataSetChange listener when contact list loaded in order to handle possible contact list updates
        dataSet.addDataSetChangeListener(this);
    }

    @Override
    public void onDataRetrievalFailed(DataRetrieval<Contact> dataRetrieval, Exception failure) {
        Log.d(LOG_TAG, "onDataRetrievalFailed " + failure.toString());
    }

    /*
    * DataSetChangeListener section
    */
    // onDataSetChanged is executed each time device contact list updated
    @Override
    public void onDataSetChanged(DataSet<Contact> dataSet, DataSetChangeType changeType, List<Integer> changedIndices) {
        Log.d(LOG_TAG, "onDataSetChanged. DataSetChangeType:" + changeType + " Indices changed:" + changedIndices);
        for (int changedIndex : changedIndices) {
            // The following condition is a workaround for an issue that will be fixed at launch.
            if (dataSet.get(changedIndex).getContactSources().equals(ContactSourceType.LOCAL)) {
                switch (changeType) {
                    case ITEMS_ADDED:
                        contacts.add(dataSet.get(changedIndex).getNativeDisplayName().getValue());
                        break;
                    case ITEMS_DELETED:
                        contacts.remove(changedIndex);
                        break;
                    case ITEMS_UPDATED:
                        contacts.set(changedIndex, dataSet.get(changedIndex).getNativeDisplayName().getValue());
                        break;
                    default:
                        break;
                }
            }
        }
        this.dataSet = dataSet;
        contactListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDataSetInvalidated(DataSet dataSet) {
        Log.d(LOG_TAG, "onDataSetInvalidated");
    }

    @Override
    public void onResume() {
        Log.d(LOG_TAG, "Fragment#onResume()");
        super.onResume();
        // Set fragment title
        getActivity().setTitle(R.string.contacts_item);
    }
}