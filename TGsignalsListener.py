import sys
import time
import re
import random
import configparser

from telethon import utils, events, TelegramClient, ConnectionMode
from telethon.tl.functions.channels import GetFullChannelRequest
from telethon.tl.types import InputChannel, PeerChat
from telethon.tl.functions.contacts import ResolveUsernameRequest
from telethon.tl.functions.messages import CheckChatInviteRequest
from telethon.extensions import markdown

class RegBlock(object):
    def __init__(self):
        self.name = ''
        self.rating = 0
        self.reg_has = None
        self.reg_skip = None
        self.reg_coin = None
        self.reg_coin2 = None
        self.reg_price_from = None
        self.reg_price_to = None
        self.reg_price_target = None

class SignalRow(object):
    def __init__(self):
        self.coin = None
        self.coin2 = None
        self.price_from = None
        self.price_to = None
        self.price_target = None
        self.rating = None
        self.date = None
        self.index = None
        self.sort = 0.0

    def init_sort(self):
        self.sort = 0.0
        if self.price_from:
            self.sort = self.sort + 1.0
        if self.price_to:
            self.sort = self.sort + 1.0
        if self.price_target:
            self.sort = self.sort + 1.0
        if self.coin2:
            self.sort = self.sort + 0.75
        self.sort = self.sort + self.rating / 20.0

    def __repr__(self):
        return str(self.rating) + ";" + self.date + ";" + self.coin + ";" + self.coin2 + ";" + self.price_from + ";" + self.price_to + ";" + self.price_target + ';' + str(self.index)

def enable_win_unicode_console():
    if sys.version_info >= (3, 6):
        return
    import win_unicode_console
    win_unicode_console.enable() 

def getFirstMatch(regex, txt):
    if regex:
        matches = re.finditer(regex, txt, re.UNICODE | re.IGNORECASE | re.MULTILINE)
        for matchNum, match in enumerate(matches):
            for groupNum in range(0, len(match.groups())):
                return match.group(1)
    return ''

def numFix(txt):
    if txt:
        txt = txt.replace(',', '.')
    if txt and (txt.find('k')>=0 or txt.find('K')>=0):
        num = float(getFirstMatch(r'^([0-9.]{2,12})', txt))
        num = num * 1000
        txt = str(num)
    return txt

def textCheck(txt, date, client):
    if (txt is None) or (txt == ''):
        return

    global signal_expr

    non_bmp_map = dict.fromkeys(range(0x10000, sys.maxunicode + 1), 0xfffd)
    txt = txt.translate(non_bmp_map)
    #print(txt)
    #print("_________________")

    stop_coin_names = [
        'BINANCE', 
        'BITTREX', 
        'YOBIT', 
        'CRYPTOPIA', 
        'SIGNAL', 
        'ZONE', 
        'UPDATE', 
        'PRICE', 
        'COIN', 
        'VIP', 
        'INSIDE', 
        'PRIVATE', 
        'AGAIN', 
        'RESULT', 
        'RESULTS', 
        'AROUND', 
        'NEAR', 
        'STOP', 
        'PART', 
        'SOME'
    ]
    best_item = None

    i = 0
    while i < len(signal_expr):
        if signal_expr[i].reg_has:
            hasmatch = getFirstMatch(signal_expr[i].reg_has, txt)
            skipmatch = getFirstMatch(signal_expr[i].reg_skip, txt)
            if hasmatch and not skipmatch:
                coin = getFirstMatch(signal_expr[i].reg_coin, txt).upper()
                coin2 = getFirstMatch(signal_expr[i].reg_coin2, txt).upper()
                price_from = numFix(getFirstMatch(signal_expr[i].reg_price_from, txt))
                price_to = numFix(getFirstMatch(signal_expr[i].reg_price_to, txt))
                price_target = numFix(getFirstMatch(signal_expr[i].reg_price_target, txt))
                if coin and coin not in stop_coin_names and coin2 not in stop_coin_names and (price_from or price_to or price_target):
                    ri = SignalRow()
                    ri.coin = coin
                    ri.coin2 = coin2
                    ri.price_from = price_from
                    ri.price_to = price_to
                    ri.price_target = price_target
                    ri.rating = signal_expr[i].rating
                    ri.date = date
                    ri.index = i + 1
                    ri.init_sort()
                    if best_item is None or best_item.sort < ri.sort:
                        best_item = ri
        i = i + 1

    if best_item is not None:
        #if best_item.coin == 'NANO':
        #    print(txt)
        print(str(best_item))

    return

def checkCurchan(channame, client):
    try:
        int_chan = int(channame)
    except:
        int_chan = 0

    if int_chan != 0:
        try:
            input_channel =  client(GetFullChannelRequest(int_chan))
            channel_id = input_channel.full_chat.id
        except Exception as e:
            print(str(e))
            return None
    else:
        try:
            response = client.invoke(ResolveUsernameRequest(channame))
            channel_id = response.peer.channel_id
        except Exception as e:
            print(str(e))
            return None

    for msg in client.get_messages(channel_id, limit=50):
        if (msg != '') and not (msg is None):
            if not hasattr(msg, 'text'):
                msg.text = None
            if msg.text is None and hasattr(msg, 'entities'):
                msg.text = markdown.unparse(msg.message, msg.entities or [])
            if msg.text is None and msg.message:
                msg.text = msg.message
            textCheck(msg.text, str(msg.date), client)

    return client.get_input_entity(channel_id)

def main():
    if not sys.argv[1] or not sys.argv[2] or not sys.argv[3]:
        return
    enable_win_unicode_console()

    global signal_expr
    signal_expr = []

    config = configparser.RawConfigParser()
    config.read('signals_listener.ini')
    session_name = config['main']['session_fname']
    api_id = sys.argv[1]
    api_hash = sys.argv[2]
    phone = sys.argv[3]

    chancount = int(config['channels']['count'])
    channames = []

    chan_index = 1
    cname = ''
    last_cname = ''
    while chan_index <= chancount:
        if cname:
            last_cname = cname
        cname = config['channel_' + str(chan_index)]['name']
        expr_b = RegBlock()
        if cname:
            channames.append(cname)
            expr_b.name = cname
        else:
            expr_b.name = last_cname
        expr_b.rating = int(config['channel_' + str(chan_index)]['signal_rating'])
        expr_b.reg_has = config['channel_' + str(chan_index)]['signal_has']
        expr_b.reg_skip = config['channel_' + str(chan_index)]['signal_skip']
        expr_b.reg_coin = config['channel_' + str(chan_index)]['signal_coin']
        expr_b.reg_coin2 = config['channel_' + str(chan_index)]['signal_coin2']
        expr_b.reg_price_from = config['channel_' + str(chan_index)]['signal_price_from']
        expr_b.reg_price_to = config['channel_' + str(chan_index)]['signal_price_to']
        expr_b.reg_price_target = config['channel_' + str(chan_index)]['signal_price_target']
        if not expr_b.reg_has:
            expr_b.reg_has = expr_b.reg_coin
        signal_expr.append(expr_b)
        chan_index = chan_index + 1

    client = TelegramClient(
        session_name,
        api_id=api_id,
        api_hash=api_hash,
        connection_mode=ConnectionMode.TCP_ABRIDGED,
        proxy=None,
        update_workers=1,
        spawn_read_thread=True
    )

    print('Connecting to Telegram servers...')
    if not client.connect():
        print('Initial connection failed. Retrying...')
        if not client.connect():
            print('Could not connect to Telegram servers.')
            return

    if not client.is_user_authorized():
        client.send_code_request(phone)
        print('Enter the code ('+phone+'): ')
        code = input()
        client.sign_in(phone, code)

    print(client.session.server_address)   # Successfull

    chan_index = 0
    while chan_index < len(channames):
        print(channames[chan_index])
        channames[chan_index] = checkCurchan(channames[chan_index], client)
        if channames[chan_index]:
            @client.on(events.NewMessage(chats=[channames[chan_index]], incoming=True))
            def normal_handler(event):
                textCheck(event.text, str(event.message.date), client)
        time.sleep(1)
        chan_index = chan_index + 1

    print('------------------')

    while True:
        time.sleep(0.1)

if __name__ == '__main__':
    main()
