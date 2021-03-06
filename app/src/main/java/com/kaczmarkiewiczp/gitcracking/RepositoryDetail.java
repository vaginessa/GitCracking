package com.kaczmarkiewiczp.gitcracking;

import android.content.Context;
import android.content.Intent;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.kaczmarkiewiczp.gitcracking.adapter.FilesAdapter;
import com.kaczmarkiewiczp.gitcracking.fragment.RepoCommitsFragment;
import com.kaczmarkiewiczp.gitcracking.fragment.RepoFilesFragment;
import com.kaczmarkiewiczp.gitcracking.fragment.RepoHomeFragment;

import org.eclipse.egit.github.core.Repository;

import java.util.ArrayList;

public class RepositoryDetail extends AppCompatActivity implements RepoCommitsFragment.BranchChangeListener, RepoFilesFragment.BranchChangeListener {

    private Repository repository;
    private Context context;
    private Toolbar toolbar;
    private ViewPager viewPager;
    private PagerAdapter pagerAdapter;
    private TabLayout tabLayout;
    private String branch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repository_detail);
        Bundle bundle = getIntent().getExtras();
        repository = (Repository) bundle.getSerializable(Consts.REPOSITORY_ARG);
        if (repository == null) {
            // something really bad happened -- return
            finish();
            return;
        }
        context = this;
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(repository.getName());
        setSupportActionBar(toolbar);
        NavBarUtils navBarUtils = new NavBarUtils(this, toolbar, NavBarUtils.NO_SELECTION);
        navBarUtils.setNavigationDrawerButtonAsUp();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        navBarUtils.killAllActivitiesOnNewActivityStart(true);

        viewPager = (ViewPager) findViewById(R.id.container);
        pagerAdapter = new PagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pagerAdapter);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate);
                if (findViewById(R.id.action_refresh) != null) {
                    findViewById(R.id.action_refresh).startAnimation(rotate);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Boolean accountHasBeenModified = data.getBooleanExtra("accountHasBeenModified", false);
            if (accountHasBeenModified) {
                new NavBarUtils(this, toolbar, NavBarUtils.NO_SELECTION);
            }
        }
    }

    @Override
    public void onBranchChanged(String branchName) {
        branch = branchName;
        for (int i = 0; i < pagerAdapter.getCount(); i++) {
            Object fragment = pagerAdapter.getFragment(i);
            if (fragment instanceof RepoCommitsFragment) {
                RepoCommitsFragment repoCommitsFragment = (RepoCommitsFragment) fragment;
                repoCommitsFragment.switchBranch(branchName);
            } else if (fragment instanceof RepoFilesFragment) {
                RepoFilesFragment repoFilesFragment = (RepoFilesFragment) fragment;
                repoFilesFragment.switchBranch(branchName);
            }
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter {

        private String tabTitles[] = new String[] {"HOME", "COMMITS", "FILES"};
        private ArrayList<Object> fragments;

        public PagerAdapter(FragmentManager fm) {
            super(fm);
            fragments = new ArrayList<>(3);
        }

        @Override
        public Fragment getItem(int position) {
            Bundle args = new Bundle();
            args.putSerializable(Consts.REPOSITORY_ARG, repository);
            if (branch != null) {
                args.putString(Consts.BRANCH_ARG, branch);
            }
            switch (position) {
                case 0:
                    RepoHomeFragment repoHomeFragment = new RepoHomeFragment();
                    repoHomeFragment.setArguments(args);
                    fragments.add(repoHomeFragment);
                    return repoHomeFragment;
                case 1:
                    RepoCommitsFragment repoCommitsFragment = new RepoCommitsFragment();
                    repoCommitsFragment.setArguments(args);
                    fragments.add(repoCommitsFragment);
                    return repoCommitsFragment;
                case 2:
                    RepoFilesFragment repoFilesFragment = new RepoFilesFragment();
                    repoFilesFragment.setArguments(args);
                    fragments.add(repoFilesFragment);
                    return repoFilesFragment;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return tabTitles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitles[position];
        }

        public Object getFragment(int position) {
            return fragments.get(position);
        }
    }
}
