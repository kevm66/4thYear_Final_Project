package com.kevinmaher.x14328981.happy;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.SupportActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.os.Bundle;


import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import com.kevinmaher.x14328981.happy.models.Mood;

import java.util.ArrayList;
import java.util.Arrays;
;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener,
        IMainActivity,
        SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "MainActivity";

    //firebase instance variables
    public FirebaseAuth mFirebaseAuth;
    public FirebaseAuth.AuthStateListener mAuthStateListener;

    //widgets
    private FloatingActionButton mFab;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    //member variables
    private String mUsername;
    public static final String ANONYMOUS = "anonymous";
    public static final int RC_SIGN_IN = 1;

    private View mParentLayout;
    private ArrayList<Mood> mMoods = new ArrayList<>();
    private MoodRecyclerViewAdapter mMoodRecyclerViewAdapter;
    private DocumentSnapshot mLastQueriedDocument;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initialize firebase components
        mFirebaseAuth = FirebaseAuth.getInstance();

        mFab = findViewById(R.id.fab);
        mParentLayout = findViewById(android.R.id.content);
        mRecyclerView = findViewById(R.id.recycler_view);
        mSwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        mFab.setOnClickListener(this);
        mSwipeRefreshLayout.setOnRefreshListener(this);

//        setupFirebaseAuth(); //TODO TOGGLE COMMENT
        initRecyclerView();
        getMoods(); //TODO TOGGLE COMMENT //CAUSES ISSUE

        //setup bottom navigation bar
//        BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.navigation);

//        bottomNavigationView.setOnNavigationItemSelectedListener
//                (new BottomNavigationView.OnNavigationItemSelectedListener() {
//                    @Override
//                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
//                        Fragment selectedFragment = null;
//                        switch (item.getItemId()) {
//                            case R.id.menu_nav_home:
//                                selectedFragment = HomeFragment.newInstance();
//                                break;
//                            case R.id.menu_nav_insights:
//                                selectedFragment = InsightsFragment.newInstance();
//                                break;
//                            case R.id.menu_nav_support:
//                                selectedFragment = SupportFragment.newInstance();
//                                break;
//                        }
//                        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
//                        transaction.replace(R.id.frame_layout, selectedFragment);
//                        transaction.commit();
//                        return true;
//                    }
//                });

//        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();   //Manually displaying the first fragment - one time only
//
//        transaction.replace(R.id.frame_layout, HomeFragment.newInstance());
//        transaction.commit();

        //bottomNavigationView.getMenu().getItem(2).setChecked(true);                       //Used to select an item programmatically

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {                         //firebase auth state check
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    //user is signed in
                    onSignedInInitialize(user.getDisplayName());
                } else {
                    //user is signed out
                    onSignedOutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(true) //set to true for production
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };
    }

    @Override
    public void deleteMood(final Mood mood) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference moodRef = db
                .collection("moods")
                .document(mood.getMood_id());

        moodRef.delete().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    makeSnackBarMessage("Mood deleted");
                    mMoodRecyclerViewAdapter.removeMood(mood);
                } else {
                    makeSnackBarMessage("Error: Unable to delete mood");
                }
            }
        });
    }

    @Override
    public void onRefresh() {
        getMoods(); //TODO TOGGLE COMMENT
        mSwipeRefreshLayout.setRefreshing(false);
    }

    //
    private void getMoods() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        CollectionReference moodsCollectionRef = db
                .collection("moods");

        Query moodsQuery = null;
        if (mLastQueriedDocument != null) {
            moodsQuery = moodsCollectionRef
                    .whereEqualTo("user_id", FirebaseAuth.getInstance().getCurrentUser().getUid())
                    .orderBy("timestamp", Query.Direction.ASCENDING)//TODO CHANGE TO DESCENDING
                    .startAfter(mLastQueriedDocument);
        } else {
            moodsQuery = moodsCollectionRef
                    .whereEqualTo("user_id", FirebaseAuth.getInstance().getCurrentUser().getUid())
                    .orderBy("timestamp", Query.Direction.ASCENDING); //TODO CHANGE TO DESCENDING
        }

        moodsQuery.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Mood mood = document.toObject(Mood.class);
                        mMoods.add(mood);
                        //                        Log.d(TAG, "onComplete: got a new mood. Position: " + (mMoods.size() - 1));
                    }
                    if (task.getResult().size() != 0) {
                        mLastQueriedDocument = task.getResult().getDocuments()
                                .get(task.getResult().size() - 1);
                    }
//                    mMoodRecyclerViewAdapter.);
                    mMoodRecyclerViewAdapter.notifyDataSetChanged();
                }
                else {
                    makeSnackBarMessage("Failed to get moods.");
                }
            }
        });


    }

    private void initRecyclerView() {
        if (mMoodRecyclerViewAdapter == null) {
            mMoodRecyclerViewAdapter = new MoodRecyclerViewAdapter(this, mMoods);
        }
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mMoodRecyclerViewAdapter);
    }

    @Override
    public void updateMood(final Mood mood) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference moodRef = db
                .collection("moods")
                .document(mood.getMood_id());

        moodRef.update(
                "title", mood.getTitle(),
                "content", mood.getContent()
        ).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    makeSnackBarMessage("Mood updated");
                    mMoodRecyclerViewAdapter.updateMood(mood);
                } else {
                    makeSnackBarMessage("Unable to update mood");
                }
            }
        });
    }

    @Override
    public void onMoodSelected(Mood mood) {
        ViewMoodDialog dialog = ViewMoodDialog.newInstance(mood);
        dialog.show(getSupportFragmentManager(), getString(R.string.dialog_view_mood));
    }

    @Override
    public void createNewMood(String title, String content) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference newMoodRef = db
                .collection("moods")
                .document();

        Mood mood = new Mood();
        mood.setTitle(title);
        mood.setContent(content);
        mood.setMood_id(newMoodRef.getId());
        mood.setUser_id(userId);

        newMoodRef.set(mood).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    makeSnackBarMessage("Mood updated");
//                    getMoods();
                } else {
                    makeSnackBarMessage("Error: Unable to update mood");
                }
            }
        });
    }

    private void makeSnackBarMessage(String message) {
        Snackbar.make(mParentLayout, message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.fab: {
                //create new mood //TODO UPDATE
                NewMoodDialog dialog = new NewMoodDialog();
                dialog.show(getSupportFragmentManager(), getString(R.string.dialog_new_mood));
//                startActivity(new Intent(MainActivity.this, UpdateActivity.class));
                break;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sign in cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);     //log user in
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);  //log user out
        }
//        detachDatabaseReadListener();//TODO DATABASE
//        mMessageAdapter.clear();//TODO DATABASE
    }


    private void onSignedInInitialize(String username) {
        mUsername = username;
//        attachDatabaseReadListener(); //TODO DATABASE
    }

    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS;
//        mMessageAdapter.clear();//TODO DATABASE
//        detachDatabaseReadListener();//TODO DATABASE
        Toast.makeText(this, "Signed out successfully!", Toast.LENGTH_SHORT).show();
    }

    //menu button
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.more, menu);
        return true;
    }

    //menu contents
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_more_filter:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.menu_more_feedback:
                Intent Email = new Intent(Intent.ACTION_SEND);
                Email.setType("text/email");
                Email.putExtra(Intent.EXTRA_EMAIL, new String[]{"kevm66@gmail.com"});
                Email.putExtra(Intent.EXTRA_SUBJECT, "Happy - Feedback");
//                Email.putExtra(Intent.EXTRA_TEXT, "Dear ...," + "");
                //                Email.putExtra(Intent.EXTRA_TEXT, "Issue: \n" + "Device: \n" + "Android Version: \n" + "App Version: \n" + "Any other comments: ");
                startActivity(Intent.createChooser(Email, "Send Feedback:"));
                return true;
            case R.id.menu_more_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.menu_more_log_out:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}

//        Log.d("myTag","DEBUG");
//        Log.e("myTag","ERROR");
//        Log.i("myTag","INFO");
//        Log.v("myTag","VERBOSE");
//        Log.e("myTag","WARN");

//        Button btnSettings = (Button)findViewById(R.id.btn_main_settings);
//
//        btnSettings.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
//            }
//        });