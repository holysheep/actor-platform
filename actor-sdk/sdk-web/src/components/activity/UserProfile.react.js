/*
 * Copyright (C) 2015-2016 Actor LLC. <https://actor.im>
 */

import React, { Component, PropTypes } from 'react';
import { Container } from 'flux/utils';
import { FormattedMessage } from 'react-intl';
import classnames from 'classnames';
import { lightbox } from '../../utils/ImageUtils';

import ActorClient from '../../utils/ActorClient';
import confirm from '../../utils/confirm';
import { escapeWithEmoji } from '../../utils/EmojiUtils';

import ContactActionCreators from '../../actions/ContactActionCreators';
import DialogActionCreators from '../../actions/DialogActionCreators';
import NotificationsActionCreators from '../../actions/NotificationsActionCreators';
import CallActionCreators from '../../actions/CallActionCreators';

import UserStore from '../../stores/UserStore';
import NotificationsStore from '../../stores/NotificationsStore';
import OnlineStore from '../../stores/OnlineStore';

import AvatarItem from '../common/AvatarItem.react';
import ContactDetails from '../common/ContactDetails.react';
import ToggleNotifications from '../common/ToggleNotifications.react';
import Fold from '../common/Fold.react';

const getStateFromStores = (uid) => {
  const thisPeer = uid ? UserStore.getUser(uid) : null;
  return {
    thisPeer,
    isNotificationsEnabled: thisPeer ? NotificationsStore.isNotificationsEnabled(thisPeer) : true,
    message: OnlineStore.getMessage()
  };
};

class UserProfile extends Component {
  static propTypes = {
    user: PropTypes.object.isRequired
  };

  static getStores() {
    return [NotificationsStore, OnlineStore];
  }

  static calculateState(prevState, nextProps) {
    return getStateFromStores(nextProps.user.id);
  }

  static contextTypes = {
    intl: PropTypes.object
  };

  constructor(props) {
    super(props);

    this.state = {
      isMoreDropdownOpen: false
    };
  }

  addToContacts = () => ContactActionCreators.addContact(this.props.user.id);

  removeFromContacts = () => {
    const { user } = this.props;
    confirm(<FormattedMessage id="modal.confirm.removeContact" values={{name: user.name}}/>).then(
      () => ContactActionCreators.removeContact(user.id),
      () => {}
    );
  };

  onNotificationChange = (event) => {
    const { thisPeer } = this.state;
    NotificationsActionCreators.changeNotificationsEnabled(thisPeer, event.target.checked);
  };

  toggleActionsDropdown = () => {
    const { isActionsDropdownOpen } = this.state;

    if (!isActionsDropdownOpen) {
      this.setState({isActionsDropdownOpen: true});
      document.addEventListener('click', this.closeActionsDropdown, false);
    } else {
      this.closeActionsDropdown();
    }
  };

  closeActionsDropdown = () => {
    this.setState({isActionsDropdownOpen: false});
    document.removeEventListener('click', this.closeActionsDropdown, false);
  };

  clearChat = (uid) => {
    const { intl } = this.context;
    confirm(intl.messages['modal.confirm.clear']).then(
      () => {
        const peer = ActorClient.getUserPeer(uid);
        DialogActionCreators.clearChat(peer);
      },
      () => {}
    );
  };

  deleteChat = (uid) => {
    const { intl } = this.context;
    confirm(intl.messages['modal.confirm.delete']).then(
      () => {
        const peer = ActorClient.getUserPeer(uid);
        DialogActionCreators.deleteChat(peer);
      },
      () => {}
    );
  };

  handleAvatarClick = () => lightbox.open(this.props.user.bigAvatar);

  makeCall = () => {
    const { user } = this.props;
    CallActionCreators.makeCall(user.id);
  };

  render() {
    const { user } = this.props;
    const { intl } = this.context;
    const { isNotificationsEnabled, isActionsDropdownOpen, message } = this.state;

    const dropdownClassNames = classnames('dropdown', {
      'dropdown--opened': isActionsDropdownOpen
    });

    return (
      <div className="activity__body user_profile">

        <ul className="profile__list">
          <li className="profile__list__item user_profile__meta">
            <header>
              <AvatarItem image={user.bigAvatar}
                          placeholder={user.placeholder}
                          size="large"
                          title={user.name}
                          onClick={this.handleAvatarClick}/>

              <h3 className="user_profile__meta__title" dangerouslySetInnerHTML={{__html: escapeWithEmoji(user.name)}}/>
              <div className="user_profile__meta__message">{message}</div>
            </header>

            {
              user.about
                ? <div className="user_profile__meta__about"
                       dangerouslySetInnerHTML={{__html: escapeWithEmoji(user.about).replace(/\n/g, '<br/>')}}/>
                : null
            }

            <footer className="row">
              <div className="col-xs">
                <button className="button button--green button--wide" onClick={this.makeCall}>
                  <i className="material-icons">phone</i>
                  {intl.messages['button.call']}
                </button>
              </div>
              <div style={{width: 10}}/>
              <div className="col-xs">
                <div className={dropdownClassNames}>
                  <button className="dropdown__button button button--flat button--wide" onClick={this.toggleActionsDropdown}>
                    <i className="material-icons">more_horiz</i>
                    {intl.messages['actions']}
                  </button>
                  <ul className="dropdown__menu dropdown__menu--right">
                    {
                      user.isContact
                        ? <li className="dropdown__menu__item" onClick={this.removeFromContacts}>
                            {intl.messages['removeFromContacts']}
                          </li>
                        : <li className="dropdown__menu__item" onClick={this.addToContacts}>
                            {intl.messages['addToContacts']}
                          </li>
                    }
                    <li className="dropdown__menu__item" onClick={() => this.clearChat(user.id)}>
                      {intl.messages['clearConversation']}
                    </li>
                    <li className="dropdown__menu__item" onClick={() => this.deleteChat(user.id)}>
                      {intl.messages['deleteConversation']}
                    </li>
                  </ul>
                </div>
              </div>
            </footer>
          </li>

          <li className="profile__list__item user_profile__contact_info no-p">
            <ContactDetails peerInfo={user}/>
          </li>

          <li className="profile__list__item user_profile__media no-p hide">
            <Fold icon="attach_file" iconClassName="icon--gray" title={intl.messages['sharedMedia']}>
              <ul>
                <li><a>230 Shared Photos and Videos</a></li>
                <li><a>49 Shared Links</a></li>
                <li><a>49 Shared Files</a></li>
              </ul>
            </Fold>
          </li>

          <li className="profile__list__item user_profile__notifications no-p">
            <ToggleNotifications isNotificationsEnabled={isNotificationsEnabled} onNotificationChange={this.onNotificationChange}/>
          </li>

        </ul>
      </div>
    );
  }
}

export default Container.create(UserProfile, {pure:false, withProps: true});
