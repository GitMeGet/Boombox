package com.example.yupeng.boombox;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.yupeng.boombox.MusicService.MusicBinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {

    private ArrayList<Song> songList;
    private ListView songView;

    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound=false;

    private boolean paused=false, playbackPaused=false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = new MenuInflater(this);
        menuInflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Animation animShake = AnimationUtils.loadAnimation(this, R.animator.shake);

        final Button button_play_pause = (Button) findViewById(R.id.button_play_pause);
        button_play_pause.setBackgroundResource(R.drawable.ic_media_pause);

        button_play_pause.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (playbackPaused == true) {
                    button_play_pause.setBackgroundResource(R.drawable.ic_media_pause);
                    playbackPaused = false;
                    musicSrv.go();
                } else {
                    button_play_pause.setBackgroundResource(R.drawable.ic_media_play);
                    playbackPaused=true;
                    musicSrv.pausePlayer();
                }
            }
        });

        final Button button_play_next = (Button) findViewById(R.id.button_play_next);
        button_play_next.setBackgroundResource(R.drawable.ic_media_next);

        button_play_next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                button_play_next.startAnimation(animShake);
                playNext();
            }
        });




        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != 	PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);

                // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an app-defined int constant
                return;
            } else
            {
                init();
            }
        }
    }

    // Displays a permission dialog when requested for devices M and above.
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        System.out.println("2");
        if (requestCode == 1) {

            // User accepts the permission(s).
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // Instantiating method/setup UI.
                init();

                // User denies the permission(s).
            } else {
                Toast.makeText(this, "Please grant the permissions for Music Player 2.0 and come" +
                        " back again soon!", Toast.LENGTH_SHORT).show();

                // Runs a thread for a slight delay prior to shutting down the app.
                Thread mthread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            sleep(1500);
                            System.exit(0);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };

                mthread.start();
            }
        }
    }

    private void init() {
        songList = new ArrayList<Song>();
        //songView = (ListView) findViewById(R.id.song_list);

        // Invokes the iteration for adding songs.
        getSongList();

        // Sorts the data so that the song titles are presented alphabetically.
        Collections.sort(songList, new Comparator<Song>(){
            public int compare(Song a, Song b){
                return a.getTitle().compareTo(b.getTitle());
            }
        });

        // Custom-base adapter instantiation that displays the songs via the ListView.
        SongAdapter songAdapter = new SongAdapter(this, songList);
        //songView.setAdapter(songAdapter);

    }

    @Override
    protected void onStart() {
        System.out.println("onStart");
        super.onStart();
        if(playIntent==null){
            System.out.println("Binding service");
            playIntent = new Intent(this, MusicService.class);
            Boolean ret_code = bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            System.out.println("Service binded: " + ret_code);

            startService(playIntent);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        paused=true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(paused){
            paused=false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        System.out.println("Destroy!");
        stopService(playIntent);
        unbindService(musicConnection);
        musicSrv=null;
        super.onDestroy();
    }

    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder)service;
            //get service
            musicSrv = binder.getService();

            System.out.println("musicSrv: " + musicSrv);

            //pass list
            musicSrv.setList(songList);
            musicBound = true;

            if(playbackPaused){
                playbackPaused=false;
            }

            musicSrv.setSong(0);
            musicSrv.playSong();

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    public void getSongList() {
        ContentResolver musicResolver = getContentResolver();
        // retrieve the URI for external music files
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        // create a Cursor instance using the ContentResolver instance to query the music files:
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if(musicCursor!=null && musicCursor.moveToFirst()){
            //get columns
            int titleColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ARTIST);
            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                songList.add(new Song(thisId, thisTitle, thisArtist));
            }
            while (musicCursor.moveToNext());
        }
    }

    public void songPicked(View view){
        musicSrv.setSong(Integer.parseInt(view.getTag().toString()));
        musicSrv.playSong();
        if(playbackPaused){
            playbackPaused=false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //menu item selected
        switch (item.getItemId()) {
            case R.id.action_shuffle:
                musicSrv.setShuffle();
                break;
            case R.id.action_end:
                stopService(playIntent);
                musicSrv = null;
                System.exit(0);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void playNext(){
        musicSrv.playNext();
        if(playbackPaused){
            playbackPaused=false;
        }
    }

    private void playPrev(){
        musicSrv.playPrev();
        if(playbackPaused){
            playbackPaused=false;
        }
    }

}