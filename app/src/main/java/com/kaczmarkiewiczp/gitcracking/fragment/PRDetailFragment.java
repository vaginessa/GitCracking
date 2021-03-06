package com.kaczmarkiewiczp.gitcracking.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.Glide;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.flexbox.FlexboxLayout;
import com.kaczmarkiewiczp.gitcracking.AccountUtils;
import com.kaczmarkiewiczp.gitcracking.Comparators;
import com.kaczmarkiewiczp.gitcracking.CreateLabelDialog;
import com.kaczmarkiewiczp.gitcracking.CreateMilestoneDialog;
import com.kaczmarkiewiczp.gitcracking.EditDialog;
import com.kaczmarkiewiczp.gitcracking.R;

import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.Milestone;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.CollaboratorService;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.LabelService;
import org.eclipse.egit.github.core.service.MilestoneService;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.ocpsoft.prettytime.PrettyTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PRDetailFragment extends Fragment
        implements CreateMilestoneDialog.MilestoneCreationListener,
        CreateLabelDialog.labelCreationListener,
        EditDialog.EditListener {

    private View rootView;
    private Context context;
    private ProgressBar loadingIndicator;
    private GitHubClient gitHubClient;
    private FloatingActionMenu floatingActionMenu;
    private SwipeRefreshLayout swipeRefreshLayout;
    private PullRequest pullRequest;
    private Repository repository;
    private Issue prIssue;
    private List<Comment> prComments;
    private List<Milestone> repositoryMilestones;
    private List<User> repositoryCollaborators;
    private List<Label> repositoryLabels;
    private CoordinatorLayout coordinatorLayout;
    private AsyncTask fetchingBackgroundTask;
    private PullRequestChangeListener changeListener;

    public interface PullRequestChangeListener {
        void onDataHasBeenModified(boolean dataHasBeenModified);
    }

    public PRDetailFragment() {
        // requires empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_pr_detail, container, false);
        rootView = view;
        context = view.getContext();
        Bundle bundle = getArguments();
        pullRequest = (PullRequest) bundle.getSerializable("pull request");
        repository = (Repository) bundle.getSerializable("repository");

        AccountUtils accountUtils = new AccountUtils(context);
        gitHubClient = accountUtils.getGitHubClient();
        floatingActionMenu = (FloatingActionMenu) view.findViewById(R.id.fab_menu);
        floatingActionMenu.setClosedOnTouchOutside(true);
        loadingIndicator = (ProgressBar) view.findViewById(R.id.pb_loading_indicator);
        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.srl_pull_request);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                    fetchingBackgroundTask.cancel(true);
                }
                fetchingBackgroundTask = new GetPRIssue().execute();
            }
        });

        setupOnClickListeners();
        setHasOptionsMenu(true);

        coordinatorLayout = (CoordinatorLayout) view.findViewById(R.id.cl_pull_request);
        Issue savedIssue = (Issue) bundle.getSerializable("prIssue");
        if (savedIssue != null && pullRequest != null) {
            prIssue = savedIssue;
            setContent();
        } else {
            coordinatorLayout.setVisibility(View.INVISIBLE);
            if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                fetchingBackgroundTask.cancel(true);
            }
            fetchingBackgroundTask = new GetPRIssue().execute();
        }

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        changeListener = (PullRequestChangeListener) context;
    }

    @Override
    public void onStop() {
        super.onStop();
        Bundle bundle = getArguments();
        bundle.putSerializable("pull request", pullRequest);
        bundle.putSerializable("prIssue", prIssue);
    }

    @Override
    public void onDestroy() {
        if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
            fetchingBackgroundTask.cancel(true);
        }
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.issue_detail, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_refresh:
                if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                    fetchingBackgroundTask.cancel(true);
                }
                fetchingBackgroundTask = new GetPRIssue().execute();
                return true;
            case R.id.action_edit:
                editPullRequest();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupOnClickListeners() {
        rootView.findViewById(R.id.fab_merge).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mergePR();
            }
        });
        rootView.findViewById(R.id.fab_close_pr).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changePRState();
            }
        });
        rootView.findViewById(R.id.fab_comment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addComment();
            }
        });
        rootView.findViewById(R.id.fab_milestone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMilestone();
            }
        });
        rootView.findViewById(R.id.fab_labels).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLabels();
            }
        });
        rootView.findViewById(R.id.fab_assignee).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAssignee();
            }
        });
    }

    private void mergePR() {
        floatingActionMenu.close(true);
        new MaterialDialog.Builder(context)
                .title("Merge Pull Request")
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .input("Optional description", pullRequest.getTitle(), new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        String description = input.toString();
                        new MergePullRequest().execute(description);
                    }
                })
                .positiveText("Merge")
                .negativeText("Cancel")
                .show();
    }

    private void changePRState() {
        floatingActionMenu.close(true);
        if (pullRequest.getState().equals("open")) {
            pullRequest.setState("closed");
        } else {
            pullRequest.setState("open");
        }
        new UpdatePullRequest().execute(pullRequest);
    }

    private void addComment() {
        floatingActionMenu.close(true);
        new MaterialDialog.Builder(context)
                .title("New comment")
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .input("Leave a comment", null, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        String comment = input.toString();
                        if (comment.isEmpty()) {
                            Toast.makeText(context, "Comment can't be empty", Toast.LENGTH_LONG).show();
                            return;
                        }
                        new NewComment().execute(comment);
                    }
                })
                .positiveText("Comment")
                .negativeText("Cancel")
                .show();
    }

    public void setMilestone() {
        floatingActionMenu.close(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Milestone");
        int currentMilestone = 0;
        final int[] selectedOption = new int[1];
        String[] options;
        if (repositoryMilestones == null) {
            options = new String[1];
        } else {
            options = new String[repositoryMilestones.size() + 1];
        }
        options[0] = "NO MILESTONE";
        int i = 1;
        if (repositoryMilestones != null) {
            for (Milestone milestone : repositoryMilestones) {
                if (prIssue.getMilestone() != null && prIssue.getMilestone().getTitle().equals(milestone.getTitle())) {
                    currentMilestone = i;
                }
                options[i++] = milestone.getTitle();
            }
        }

        builder.setSingleChoiceItems(options, currentMilestone, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedOption[0] = which;
            }
        });
        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (selectedOption[0] == 0) {
                    Milestone noMilestone = new Milestone();
                    noMilestone.setTitle("");
                    prIssue.setMilestone(noMilestone);
                } else {
                    Milestone newMilestone = repositoryMilestones.get(selectedOption[0] - 1);
                    prIssue.setMilestone(newMilestone);
                }
                new UpdatePRIssue().execute(prIssue);
            }
        });
        final CreateMilestoneDialog milestoneDialog = new CreateMilestoneDialog();
        milestoneDialog.setTargetFragment(this, 0);
        builder.setNeutralButton("New Milestone", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                milestoneDialog.show(getFragmentManager(), "New Milestone");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onSaveMilestone(CreateMilestoneDialog dialog) {
        String title = dialog.getMilestoneTitle();
        String description = dialog.getMilestoneDescription();

        Milestone milestone = new Milestone();
        milestone.setTitle(title);
        milestone.setDescription(description);
        milestone.setState("open");
        if (dialog.isMilestoneDueDateSet()) {
            milestone.setDueOn(dialog.getMilestoneDueDate());
        }
        dialog.dismiss();
        Snackbar.make(rootView.findViewById(R.id.cl_pull_request), "Milestone created", Snackbar.LENGTH_LONG)
                .show();

        new NewMilestone().execute(milestone);
    }

    private void setAssignee() {
        floatingActionMenu.close(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Assignee");
        int currentAssignee = 0;
        final int[] selectedOption = new int[1];
        String[] options;
        if (repositoryCollaborators == null) {
            options = new String[1];
        } else {
            options = new String[repositoryCollaborators.size() + 1];
        }
        options[0] = "--NO ASSIGNEE--";
        int i = 1;
        if (repositoryCollaborators != null) {
            for (User collaborator : repositoryCollaborators) {
                if (prIssue.getAssignee() != null && prIssue.getAssignee().getLogin().equals(collaborator.getLogin())) {
                    currentAssignee = i;
                }
                options[i++] = collaborator.getLogin();
            }
        }

        builder.setSingleChoiceItems(options, currentAssignee, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedOption[0] = which;
            }
        });
        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (selectedOption[0] == 0) {
                    User emptyUser = new User();
                    emptyUser.setLogin("");
                    prIssue.setAssignee(emptyUser);
                } else {
                    User collaborator = repositoryCollaborators.get(selectedOption[0] - 1);
                    prIssue.setAssignee(collaborator);
                }
                new UpdatePRIssue().execute(prIssue);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setLabels() {
        floatingActionMenu.close(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Labels");
        String[] options = new String[repositoryLabels.size()]; // TODO crash if there are no labels
        final List<Label> issueLabels = prIssue.getLabels();
        final boolean[] selection = new boolean[repositoryLabels.size()]; // TODO crash if no labels
        if (repositoryLabels != null) {
            for (int i = 0; i < repositoryLabels.size(); i++) {
                Label label = repositoryLabels.get(i);
                options[i] = label.getName();
                selection[i] = issueLabels.contains(label);
            }
        }
        builder.setMultiChoiceItems(options, selection, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                selection[which] = isChecked;
            }
        });

        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                List<Label> newLabels = new ArrayList<>();
                for (int i = 0; i < selection.length; i++) {
                    if (selection[i]) {
                        newLabels.add(repositoryLabels.get(i));
                    }
                }
                prIssue.setLabels(newLabels);
                new UpdatePRIssue().execute(prIssue);
            }
        });
        builder.setNegativeButton("Cancel", null);
        final CreateLabelDialog createLabelDialog = new CreateLabelDialog();
        createLabelDialog.setTargetFragment(this, 0);
        builder.setNeutralButton("New Label", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                createLabelDialog.show(getFragmentManager(), "New Label");
            }
        });
        builder.show();
    }

    @Override
    public void onSaveLabel(CreateLabelDialog dialog) {
        String labelName = dialog.getLabelName();
        String labelColor = dialog.getLabelHexColor();

        Label label = new Label();
        label.setName(labelName);
        label.setColor(labelColor);

        dialog.dismiss();
        Snackbar.make(rootView.findViewById(R.id.cl_pull_request), "Label created", Snackbar.LENGTH_LONG)
                .show();
        new NewLabel().execute(label);
    }

    private void setContent() {
        setPRStateContent();
        setTitleContent();
        setRepositoryContent();
        setUserContent();
        setMilestoneContent();
        setLabelsContent();
        setAssigneeContent();
        setDescriptionContent();
        setCommentContent();
    }

    @SuppressWarnings("deprecation") // for getColor -- check in code for android version
    private void setPRStateContent() {
        String status = prIssue.getState();
        status = status.substring(0, 1).toUpperCase() + status.substring(1);
        boolean isMergeable = pullRequest.isMergeable();
        boolean isClosed = pullRequest.getState().equals("closed");
        boolean isMerged = pullRequest.isMerged();

        LinearLayout linearLayoutMergeable = (LinearLayout) rootView.findViewById(R.id.ll_pr_conflicts);
        LinearLayout linearLayoutPRStatus = (LinearLayout) rootView.findViewById(R.id.ll_pr_status);
        ImageView imageViewPRStatus = (ImageView) rootView.findViewById(R.id.iv_pr_status);
        TextView textViewPRStatus = (TextView) rootView.findViewById(R.id.tv_pr_status);
        FloatingActionButton fabMerge = (FloatingActionButton) rootView.findViewById(R.id.fab_merge);
        FloatingActionButton fabClose = (FloatingActionButton) rootView.findViewById(R.id.fab_close_pr);

        int color;
        if (status.equals("Open")) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                color = context.getColor(R.color.pr_open);
            } else {
                color = getResources().getColor(R.color.pr_open);
            }
            imageViewPRStatus.setImageResource(R.drawable.ic_git_pull_request_white);
            linearLayoutPRStatus.setBackgroundColor(color);
            textViewPRStatus.setText(status);
        } else if (status.equals("Closed") && !isMerged) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                color = context.getColor(R.color.pr_closed);
            } else {
                color = getResources().getColor(R.color.pr_closed);
            }
            imageViewPRStatus.setImageResource(R.drawable.ic_git_merge_white);
            linearLayoutPRStatus.setBackgroundColor(color);
            textViewPRStatus.setText(status);
        } else if (status.equals("Closed") && isMerged) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                color = context.getColor(R.color.pr_merged);
            } else {
                color = getResources().getColor(R.color.pr_merged);
            }
            imageViewPRStatus.setImageResource(R.drawable.ic_git_pull_request_white);
            linearLayoutPRStatus.setBackgroundColor(color);
            textViewPRStatus.setText("Merged");

        } else {
            linearLayoutPRStatus.setVisibility(View.GONE);
            textViewPRStatus.setText(status);
        }

        if (isMergeable) {
            linearLayoutMergeable.setVisibility(View.GONE);
            fabMerge.setVisibility(View.VISIBLE);
        } else if (!isMerged) {
            linearLayoutMergeable.setVisibility(View.VISIBLE);
            fabMerge.setVisibility(View.GONE);
        }

        if (isClosed) {
            fabClose.setLabelText("Reopen");
            fabClose.setImageDrawable(context.getDrawable(R.drawable.ic_check_black_24dp));
            fabMerge.setVisibility(View.GONE);
        } else {
            fabClose.setLabelText("Close");
            fabClose.setImageDrawable(context.getDrawable(R.drawable.ic_close_white_24dp));
        }

        if (isMerged) {
            fabClose.setVisibility(View.GONE);
            fabMerge.setVisibility(View.GONE);
        }
    }

    private void setTitleContent() {
        String title = prIssue.getTitle();
        String prNumber = "#" + String.valueOf(prIssue.getNumber());

        TextView textViewTitle = (TextView) rootView.findViewById(R.id.tv_pr_title);
        TextView textViewIssueNumber = (TextView) rootView.findViewById(R.id.tv_pr_number);

        textViewTitle.setText(title);
        textViewIssueNumber.setText(prNumber);
    }

    private void setRepositoryContent() {
        String repositoryName = repository.getName();
        String repositoryOwner = repository.getOwner().getLogin();
        repositoryName = repositoryOwner + "/" + repositoryName;
        String branch = pullRequest.getHead().getLabel();
        int startIndexOfBranch = branch.indexOf(':');
        branch = branch.substring(startIndexOfBranch + 1);

        TextView textViewRepositoryName = (TextView) rootView.findViewById(R.id.tv_pr_repo);
        TextView textViewBranchName = (TextView) rootView.findViewById(R.id.tv_branch);

        textViewRepositoryName.setText(repositoryName);
        textViewBranchName.setText(branch);

    }

    private void setUserContent() {
        String userLogin = prIssue.getUser().getLogin();
        String userIconUrl = prIssue.getUser().getAvatarUrl();
        Date date = prIssue.getCreatedAt();
        PrettyTime prettyTime = new PrettyTime();
        String action = "opened this pull request";

        ImageView imageViewUserIcon = (ImageView) rootView.findViewById(R.id.iv_user_icon);
        TextView textViewUser = (TextView) rootView.findViewById(R.id.tv_user_login);
        TextView textViewAction = (TextView) rootView.findViewById(R.id.tv_open_close_action);
        TextView textViewDate = (TextView) rootView.findViewById(R.id.tv_date);

        Glide
                .with(this)
                .load(userIconUrl)
                .error(context.getDrawable(android.R.drawable.sym_def_app_icon))
                .placeholder(R.drawable.progress_animation)
                .crossFade()
                .into(imageViewUserIcon);
        textViewUser.setText(userLogin);
        textViewAction.setText(action);
        textViewDate.setText(prettyTime.format(date));
    }

    @SuppressWarnings("deprecation") // for getColor -- check in code for android version
    private void setMilestoneContent() {
        Milestone milestone = prIssue.getMilestone();
        LinearLayout linearLayoutMilestone = (LinearLayout) rootView.findViewById(R.id.ll_milestone);
        if (milestone == null) {
            // milestone is the first element for the section, if (after refresh) there is no milestone
            // for this issue, make the divider invisible (it could have been visible before refresh)
            // other elements (assignee, labels) don't have to do this, and if they're visible, they
            // will set the divider to visible
            setDescriptionDivider(false);
            linearLayoutMilestone.setVisibility(View.GONE);
            return;
        }

        TextView textViewMilestone = (TextView) rootView.findViewById(R.id.tv_milestone);
        ProgressBar progressBarMilestone = (ProgressBar) rootView.findViewById(R.id.pb_milestone);

        String milestoneTitle = milestone.getTitle();
        int openIssues = milestone.getOpenIssues();
        int closedIssues = milestone.getClosedIssues();

        linearLayoutMilestone.setVisibility(View.VISIBLE);
        textViewMilestone.setText(milestoneTitle);
        progressBarMilestone.setMax(openIssues + closedIssues);
        progressBarMilestone.setProgress(closedIssues);

        Date dueDate = milestone.getDueOn();
        Date today = new Date();
        int progressColor;
        if (dueDate != null) {
            if (today.after(dueDate)) {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    progressColor = context.getColor(R.color.milestone_progress_overdue);
                } else {
                    progressColor = getResources().getColor(R.color.milestone_progress_overdue);
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    progressColor = context.getColor(R.color.milestone_progress);
                } else {
                    progressColor = getResources().getColor(R.color.milestone_progress);
                }
            }
            progressBarMilestone.getProgressDrawable().setColorFilter(progressColor, PorterDuff.Mode.SRC_IN);
        }
        setDescriptionDivider(true);
    }

    private void setLabelsContent() {
        List<Label> labels = prIssue.getLabels();
        LinearLayout linearLayoutLabels = (LinearLayout) rootView.findViewById(R.id.ll_tags);

        if (labels == null || labels.size() < 1) {
            linearLayoutLabels.setVisibility(View.GONE);
            return;
        }
        linearLayoutLabels.setVisibility(View.VISIBLE);
        FlexboxLayout flexboxLayoutLabels = (FlexboxLayout) rootView.findViewById(R.id.fl_tags);
        if (flexboxLayoutLabels.getChildCount() > 0) {
            flexboxLayoutLabels.removeAllViews();
        }
        for (Label label : labels) {
            String colorString = label.getColor();
            int rgb = Color.parseColor("#" + colorString);
            int r = Color.red(rgb);
            int g = Color.green(rgb);
            int b = Color.blue(rgb);
            double a = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255;

            TextView textViewLabel = new TextView(context);
            textViewLabel.setText(label.getName());
            textViewLabel.setBackgroundColor(Color.parseColor("#" + colorString));
            if (a < 0.5) {
                textViewLabel.setTextColor(Color.BLACK);
            } else {
                textViewLabel.setTextColor(Color.WHITE);
            }
            textViewLabel.setElevation(4);
            textViewLabel.setPadding(8, 4, 8, 4);
            FlexboxLayout.LayoutParams labelLayoutParams = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLayoutParams.setMargins(0, 0, 12, 12);
            textViewLabel.setLayoutParams(labelLayoutParams);
            flexboxLayoutLabels.addView(textViewLabel);
        }
        setDescriptionDivider(true);
    }

    private void setAssigneeContent() {
        User assignee = prIssue.getAssignee();
        LinearLayout linearLayoutAssignee = (LinearLayout) rootView.findViewById(R.id.ll_assignee);
        if (assignee == null) {
            linearLayoutAssignee.setVisibility(View.GONE);
            return;
        }

        String assigneeName = assignee.getLogin();
        String assigneeIconUrl = assignee.getAvatarUrl();

        TextView textViewAssigneeName = (TextView) rootView.findViewById(R.id.tv_assignee_name);
        ImageView imageViewAssigneeIcon = (ImageView) rootView.findViewById(R.id.iv_assignee_icon);

        linearLayoutAssignee.setVisibility(View.VISIBLE);
        Glide
                .with(this)
                .load(assigneeIconUrl)
                .error(context.getDrawable(android.R.drawable.sym_def_app_icon))
                .placeholder(R.drawable.progress_animation)
                .crossFade()
                .into(imageViewAssigneeIcon);
        textViewAssigneeName.setText(assigneeName);

        setDescriptionDivider(true);
    }

    @SuppressWarnings("deprecation") // for Html.fromHtml -- check in code for android version
    private void setDescriptionContent() {
        String description = prIssue.getBodyHtml();
        TextView textViewDescription = (TextView) rootView.findViewById(R.id.tv_pr_description);

        if (description == null || description.isEmpty()) {
            textViewDescription.setVisibility(View.GONE);
            return;
        }

        textViewDescription.setVisibility(View.VISIBLE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            textViewDescription.setText(Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT));
        } else {
            textViewDescription.setText(Html.fromHtml(description));
        }

        setDescriptionDivider(true);
    }

    private void setDescriptionDivider(boolean visible) {
        View separator = rootView.findViewById(R.id.description_divider);
        if (visible) {
            if (separator.getVisibility() == View.GONE) {
                separator.setVisibility(View.VISIBLE);
            }
        } else {
            if (separator.getVisibility() != View.GONE) {
                separator.setVisibility(View.GONE);
            }
        }
    }

    @SuppressWarnings("deprecation") // for Html.fromHtml -- check in code for android version
    private void setCommentContent() {
        LinearLayout linearLayout = (LinearLayout) rootView.findViewById(R.id.ll_comments);
        PrettyTime prettyTime = new PrettyTime();
        int layoutIdForListItem = R.layout.comment_item;

        if (prComments == null || prComments.isEmpty()) {
            linearLayout.setVisibility(View.GONE);
            return;
        } else {
            linearLayout.setVisibility(View.VISIBLE);
            linearLayout.removeAllViews();
        }

        for (final Comment comment : prComments) {
            LayoutInflater inflater = LayoutInflater.from(context);

            View view = inflater.inflate(layoutIdForListItem, null);
            ImageView imageViewUserIcon = (ImageView) view.findViewById(R.id.iv_comment_icon);
            TextView textViewLogin = (TextView) view.findViewById(R.id.tv_comment_login);
            TextView textViewDate = (TextView) view.findViewById(R.id.tv_comment_date);
            TextView textViewComment = (TextView) view.findViewById(R.id.tv_comment);
            final ImageButton imageButtonEdit = (ImageButton) view.findViewById(R.id.ib_edit_comment);
            imageButtonEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popupMenu = new PopupMenu(context, imageButtonEdit);
                    popupMenu.getMenuInflater().inflate(R.menu.comment_edit_menu, popupMenu.getMenu());
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.edit:
                                    editComment(comment);
                                    break;
                                case R.id.delete:
                                    deleteComment(comment);
                                    break;
                                default:
                                    return false;
                            }
                            return true;
                        }
                    });
                    popupMenu.show();
                }
            });

            Glide
                    .with(this)
                    .load(comment.getUser().getAvatarUrl())
                    .error(context.getDrawable(android.R.drawable.sym_def_app_icon))
                    .placeholder(R.drawable.progress_animation)
                    .crossFade()
                    .into(imageViewUserIcon);
            textViewLogin.setText(comment.getUser().getLogin());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                textViewComment.setText(Html.fromHtml(comment.getBodyHtml(), Html.FROM_HTML_MODE_COMPACT));
            } else {
                textViewComment.setText(Html.fromHtml(comment.getBodyHtml()));
            }
            String commentedDate = "commented " + prettyTime.format(comment.getCreatedAt());
            textViewDate.setText(commentedDate);
            linearLayout.addView(view);
        }
    }

    private void editComment(final Comment comment) {
        String commentText = comment.getBodyText();
        new MaterialDialog.Builder(context)
                .title("Edit comment")
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .input(null, commentText, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        String newCommentText = input.toString();
                        if (newCommentText.isEmpty()) {
                            Toast.makeText(context, "Comment can't be empty", Toast.LENGTH_LONG).show();
                        }
                        comment.setBody(newCommentText);
                        new EditComment().execute(comment);
                    }
                })
                .positiveText("Submit")
                .negativeText("Cancel")
                .show();
    }

    private void deleteComment(final Comment comment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Delete comment");
        builder.setMessage("Delete this comment?");
        builder.setNegativeButton("No", null);
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String commentId = String.valueOf(comment.getId());
                new DeleteComment().execute(commentId);
            }
        });
        builder.show();
    }

    private void editPullRequest() {
        EditDialog editDialog = new EditDialog();
        editDialog.setTargetFragment(this, 0);

        String toolbarTitle = "Edit Pull Request #" + pullRequest.getNumber();
        String currentTitle = pullRequest.getTitle();
        String currentDescription = pullRequest.getBody();

        editDialog.setTitle(toolbarTitle);
        editDialog.setTitle(currentTitle);
        editDialog.setDescription(currentDescription);
        editDialog.setTitleHint("Pull Request title");
        editDialog.setDescriptionHint("Optional Pull Request description");
        editDialog.setToolbarTitle(toolbarTitle);

        editDialog.show(getFragmentManager(), "Edit Pull Request");
    }

    @Override
    public void onSaveEdit(EditDialog editDialog, String title, String description) {
        if (pullRequest.getTitle().equals(title) && pullRequest.getBody().equals(description)) {
            // ignore if there are no changes
            editDialog.dismiss();
            return;
        }
        pullRequest.setTitle(title);
        pullRequest.setBody(description);
        editDialog.dismiss();
        new UpdatePullRequest().execute(pullRequest);
    }

    /***********************************************************************************************
     * Background tasks classes
     **********************************************************************************************/

    private class GetPRIssue extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!swipeRefreshLayout.isRefreshing()) {
                loadingIndicator.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected Boolean doInBackground(Void... aVoid) {
            IssueService issueService = new IssueService(gitHubClient);
            PullRequestService pullRequestService = new PullRequestService(gitHubClient);
            MilestoneService milestoneService = new MilestoneService(gitHubClient);
            CollaboratorService collaboratorService = new CollaboratorService(gitHubClient);
            LabelService labelService = new LabelService(gitHubClient);

            try {
                prIssue = issueService.getIssue(repository, pullRequest.getNumber());
                pullRequest = pullRequestService.getPullRequest(repository, pullRequest.getNumber());
                prComments = issueService.getComments(repository, prIssue.getNumber());
                repositoryMilestones = milestoneService.getMilestones(repository, "open");
                repositoryCollaborators = collaboratorService.getCollaborators(repository);
                repositoryLabels = labelService.getLabels(repository);

                Collections.sort(repositoryMilestones, new Comparators.MilestonesComparator());
                Collections.sort(repositoryCollaborators, new Comparators.CollaboratorComparator());
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (!success) {
                return;
            }
            setContent();
            if (coordinatorLayout.getVisibility() == View.INVISIBLE) {
                coordinatorLayout.setVisibility(View.VISIBLE);
            }
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            } else if (loadingIndicator.getVisibility() == View.VISIBLE) {
                loadingIndicator.setVisibility(View.GONE);
            }
        }
    }

    private class MergePullRequest extends AsyncTask<String, Void, Boolean> {

        private PullRequest updatedPR;
        private MaterialDialog loadingDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingDialog = new MaterialDialog.Builder(context)
                    .title("Merging a Pull Request")
                    .content("Please wait")
                    .progress(true, 0)
                    .show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String pullRequestMessage = params[0];
            PullRequestService pullRequestService = new PullRequestService(gitHubClient);
            int pullRequestNumber = pullRequest.getNumber();

            try {
                updatedPR = pullRequestService.getPullRequest(repository, pullRequestNumber);

                if (updatedPR.isMergeable()) {
                    pullRequestService.merge(repository, pullRequestNumber, pullRequestMessage);
                } else {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            loadingDialog.dismiss();
            changeListener.onDataHasBeenModified(true);
            fetchingBackgroundTask = new GetPRIssue().execute();
            if (!success && !updatedPR.isMergeable()) {
                new MaterialDialog.Builder(context)
                        .title("Error")
                        .content("Pull Request cannot be merged")
                        .positiveText("Dismiss")
                        .show();
            }
        }
    }

    private class UpdatePullRequest extends AsyncTask<PullRequest, Void, Boolean> {

        private PullRequest updatedPR;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(PullRequest... params) {
            PullRequest pullRequest = params[0];
            PullRequestService pullRequestService = new PullRequestService(gitHubClient);

            try {
                updatedPR = pullRequestService.editPullRequest(repository, pullRequest);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                pullRequest = updatedPR;
                changeListener.onDataHasBeenModified(true);
                fetchingBackgroundTask = new GetPRIssue().execute();
            } else {
                loadingIndicator.setVisibility(View.GONE);
            }
        }
    }
    private class UpdatePRIssue extends AsyncTask<Issue, Void, Boolean> {

        Issue updatedPRIssue;

        @Override
        protected Boolean doInBackground(Issue... params) {
            Issue issue = params[0];
            IssueService issueService = new IssueService(gitHubClient);

            try {
                updatedPRIssue = issueService.editIssue(repository, issue);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (!success) {
                return;
            }
            prIssue = updatedPRIssue;
            changeListener.onDataHasBeenModified(true);
            setContent();
            if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                fetchingBackgroundTask.cancel(true);
            }
            fetchingBackgroundTask = new GetPRIssue().execute();
        }
    }

    private class NewLabel extends AsyncTask<Label, Void, Boolean> {

        private Label newLabel;

        @Override
        protected Boolean doInBackground(Label... params) {
            Label label = params[0];
            LabelService labelService = new LabelService(gitHubClient);

            try {
                newLabel = labelService.createLabel(repository, label);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                List<Label> labels = prIssue.getLabels();
                labels.add(newLabel);
                prIssue.setLabels(labels);
                new UpdatePRIssue().execute(prIssue);
            }
        }
    }

    private class NewMilestone extends AsyncTask<Milestone, Void, Boolean> {

        private Milestone newMilestone;

        @Override
        protected Boolean doInBackground(Milestone... params) {
            Milestone milestone = params[0];
            MilestoneService milestoneService = new MilestoneService(gitHubClient);

            try {
                newMilestone = milestoneService.createMilestone(repository, milestone);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                prIssue.setMilestone(newMilestone);
                new UpdatePRIssue().execute(prIssue);
            }
        }
    }

    private class NewComment extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String comment = params[0];
            IssueService issueService = new IssueService(gitHubClient);

            try {
                issueService.createComment(repository, prIssue.getNumber(), comment);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                CoordinatorLayout coordinatorLayout = (CoordinatorLayout) rootView.findViewById(R.id.cl_pull_request);
                Snackbar.make(coordinatorLayout, "Comment created", Snackbar.LENGTH_LONG).show();

                changeListener.onDataHasBeenModified(true);

                if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                    fetchingBackgroundTask.cancel(true);
                }
                fetchingBackgroundTask = new GetPRIssue().execute();
            }
        }
    }

    private class EditComment extends AsyncTask<Comment, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Comment... params) {
            Comment comment = params[0];
            IssueService issueService = new IssueService(gitHubClient);

            try {
                issueService.editComment(repository, comment);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                CoordinatorLayout coordinatorLayout = (CoordinatorLayout) rootView.findViewById(R.id.cl_pull_request);
                Snackbar.make(coordinatorLayout, "Comment edited", Snackbar.LENGTH_LONG).show();

                if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                    fetchingBackgroundTask.cancel(true);
                }
                fetchingBackgroundTask = new GetPRIssue().execute();
            }
        }
    }

    private class DeleteComment extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String commentId = params[0];
            IssueService issueService = new IssueService(gitHubClient);

            try {
                issueService.deleteComment(repository, commentId);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                CoordinatorLayout coordinatorLayout = (CoordinatorLayout) rootView.findViewById(R.id.cl_pull_request);
                Snackbar.make(coordinatorLayout, "Comment removed", Snackbar.LENGTH_LONG).show();

                changeListener.onDataHasBeenModified(true);

                if (fetchingBackgroundTask != null && fetchingBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                    fetchingBackgroundTask.cancel(true);
                }
                fetchingBackgroundTask = new GetPRIssue().execute();
            }
        }
    }
}
