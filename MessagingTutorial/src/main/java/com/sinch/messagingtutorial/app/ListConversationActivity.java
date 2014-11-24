package com.sinch.messagingtutorial.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.parse.FindCallback;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Peter on 2014-11-23.
 */
public class ListConversationActivity extends Activity {

    public static final String ID_SEPARATOR = ";:;";
    public static final String IS_GROUP = "is_group";
    public static final String RECIPIENT_IDS = "recipient_ids";

    private String currentUserId;
    private ArrayAdapter<String> namesArrayAdapter;
    private ArrayList<String> names;
    private ArrayList<String> ids;
    private ListView conversationListView;
    private Button logoutButton;
    private ProgressDialog progressDialog;
    private BroadcastReceiver receiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_conversations);

        showSpinner();

        Intent serviceIntent = new Intent(getApplicationContext(), MessageService.class);
        startService(serviceIntent);

        logoutButton = (Button) findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(getApplicationContext(), MessageService.class));
                ParseUser.logOut();
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation_list_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_start_new:
                openNewDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void openNewDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select type of conversation")
                .setItems(new String[]{"Group", "Individual"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            openGroupDialog();
                        } else {
                            openIndividualDialog();
                        }
                    }
                });
        builder.create().show();
    }

    private void showGroupDialog(String[] usernameList, final String[] ids, Activity contextActivity) {
        final ArrayList<Integer> selectedItems = new ArrayList();  // Where we track the selected items
        AlertDialog.Builder builder = new AlertDialog.Builder(contextActivity);
        // Set the dialog title
        builder.setTitle("Pick the users in the conversation")
                // Specify the list array, the items to be selected by default (null for none),
                // and the listener through which to receive callbacks when items are selected
                .setMultiChoiceItems(usernameList, null,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                if (isChecked) {
                                    // If the user checked the item, add it to the selected items
                                    selectedItems.add(which);
                                } else if (selectedItems.contains(which)) {
                                    // Else, if the item is already in the array, remove it
                                    selectedItems.remove(Integer.valueOf(which));
                                }
                            }
                        })
                        // Set the action buttons
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        ArrayList<String> selectedIds = new ArrayList<String>();
                        for (int pos : selectedItems) {
                            selectedIds.add(ids[pos]);
                        }
                        openGroupConversation(selectedIds);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

        builder.create().show();
    }

    private void openGroupDialog() {
        final ArrayList<String> nameOptions = new ArrayList<String>();
        final ArrayList<String> ids = new ArrayList<String>();
        final Activity context = this;

        ParseQuery<ParseUser> query = ParseUser.getQuery();
        query.whereNotEqualTo("objectId", currentUserId);
        query.findInBackground(new FindCallback<ParseUser>() {
            public void done(List<ParseUser> userList, com.parse.ParseException e) {
                if (e == null) {
                    for (ParseUser user : userList) {
                        nameOptions.add(user.getUsername());
                        ids.add(user.getObjectId());
                    }
                    showGroupDialog(nameOptions.toArray(new String[]{}), ids.toArray(new String[]{}), context);
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error loading user list",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void openIndividualDialog() {
        final ArrayList<String> nameOptions = new ArrayList<String>();
        final Activity context = this;

        ParseQuery<ParseUser> query = ParseUser.getQuery();
        query.whereNotContainedIn("objectId", ids);
        query.findInBackground(new FindCallback<ParseUser>() {
            public void done(List<ParseUser> userList, com.parse.ParseException e) {
                if (e == null) {
                    for (ParseUser user : userList) {
                        nameOptions.add(user.getUsername());
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Select User")
                            .setItems(nameOptions.toArray(new String[]{}), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    openConversation(nameOptions, which);
                                }
                            });
                    builder.create().show();
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error loading user list",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //show a loading spinner while the sinch client starts
    private void showSpinner() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Loading");
        progressDialog.setMessage("Please wait...");
        progressDialog.show();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Boolean success = intent.getBooleanExtra("success", false);
                progressDialog.dismiss();
                if (!success) {
                    Toast.makeText(getApplicationContext(), "Messaging service failed to start", Toast.LENGTH_LONG).show();
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("com.sinch.messagingtutorial.app.ListUsersActivity"));
    }

    //display clickable a list of all users
    private void setConversationsList() {
        currentUserId = ParseUser.getCurrentUser().getObjectId();
        names = new ArrayList<String>();
        ids = new ArrayList<String>();
        ids.add(currentUserId);

        ParseQuery<ParseUser> query = ParseUser.getQuery();
        query.whereNotEqualTo("objectId", currentUserId);
        query.findInBackground(new FindCallback<ParseUser>() {
            public void done(List<ParseUser> userList, com.parse.ParseException e) {
                if (e == null) {
                    for (int i = 0; i < userList.size(); i++) {
                        ParseQuery<ParseObject> query = ParseQuery.getQuery("ParseMessage");
                        List<String> idList = Arrays.asList(new String[]{userList.get(i).getObjectId(), currentUserId});
                        query.whereContainedIn("senderId", idList);
                        query.whereContainedIn("recipientId", idList);
                        query.orderByAscending("createdAt");

                        try {
                            if (query.count() > 0) {
                                names.add(userList.get(i).getUsername().toString());
                                ids.add(userList.get(i).getObjectId());
                            }
                        } catch (Exception exception) {
                        }
                    }

                    conversationListView = (ListView)findViewById(R.id.conversationListView);
                    namesArrayAdapter =
                            new ArrayAdapter<String>(getApplicationContext(),
                                    R.layout.user_list_item, names);
                    conversationListView.setAdapter(namesArrayAdapter);

                    conversationListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> a, View v, int i, long l) {
                            openConversation(names, i);
                        }
                    });

                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error loading user list",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //open a conversation with one person
    public void openConversation(ArrayList<String> names, int pos) {
        ParseQuery<ParseUser> query = ParseUser.getQuery();
        query.whereEqualTo("username", names.get(pos));
        query.findInBackground(new FindCallback<ParseUser>() {
            public void done(List<ParseUser> user, com.parse.ParseException e) {
                if (e == null) {
                    Intent intent = new Intent(getApplicationContext(), MessagingActivity.class);
                    intent.putExtra(RECIPIENT_IDS, user.get(0).getObjectId());
                    intent.putExtra(IS_GROUP, false);
                    startActivity(intent);
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error finding that user",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void openGroupConversation(ArrayList<String> selectedIds) {
        String recipientString = "";
        for (String id : selectedIds) {
            recipientString += id + ID_SEPARATOR;
        }
        Intent intent = new Intent(getApplicationContext(), MessagingActivity.class);
        intent.putExtra(RECIPIENT_IDS, recipientString);
        intent.putExtra(IS_GROUP, true);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        setConversationsList();
        super.onResume();
    }
}
